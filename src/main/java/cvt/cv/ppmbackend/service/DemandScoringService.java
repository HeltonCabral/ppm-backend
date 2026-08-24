package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandScoringDtos.DemandScoringResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.DimensionScoreResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.ScoringItemResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.UpsertRequest;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandScoring;
import cvt.cv.ppmbackend.entity.ScoringCriterion;
import cvt.cv.ppmbackend.entity.ScoringDimension;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.ScoringImpactType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.DemandScoringRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
@Transactional
public class DemandScoringService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Set<DemandPlanningStatus> AUTO_STATUS_SCOPE = Set.of(
            UNDER_ANALYSIS, PRIORITIZATION, READY_FOR_COMMITTEE);
    private final DemandPlanningCycleRepository cycles;
    private final DemandScoringRepository scoring;
    private final ScoringCriterionService criteria;
    private final DemandScoreLifecycleService scoreLifecycle;
    private final DemandPlanningCycleService planningCycleService;

    public DemandScoringService(DemandPlanningCycleRepository cycles, DemandScoringRepository scoring,
            ScoringCriterionService criteria, DemandScoreLifecycleService scoreLifecycle,
            DemandPlanningCycleService planningCycleService) {
        this.cycles = cycles;
        this.scoring = scoring;
        this.criteria = criteria;
        this.scoreLifecycle = scoreLifecycle;
        this.planningCycleService = planningCycleService;
    }

    public DemandScoringResponse getByCycle(DemandPlanningCycle cycle) {
        List<DemandScoring> items = scoring.findByPlanningCycle_IdOrderByCriterionOrderIndexAsc(cycle.getId());
        return mapResponse(cycle, items);
    }

    public DemandScoringResponse getByDemandAndPlan(UUID demandId, UUID strategicPlanId) {
        return getByCycle(resolveCycle(demandId, strategicPlanId));
    }

    public DemandScoringResponse upsert(UUID demandId, UUID strategicPlanId, UpsertRequest request, String actorId) {
        return upsert(resolveCycle(demandId, strategicPlanId), request, actorId);
    }

    private DemandPlanningCycle resolveCycle(UUID demandId, UUID strategicPlanId) {
        return cycles.findByDemand_IdAndStrategicPlan_Id(demandId, strategicPlanId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ciclo de planeamento não encontrado para esta demanda no plano informado"));
    }

    public DemandScoringResponse upsert(DemandPlanningCycle cycle, UpsertRequest request, String actorId) {
        scoreLifecycle.validateCanScore(cycle);
        String actor = actorId == null || actorId.isBlank() ? "system" : actorId;
        boolean fullReplacement = scoreLifecycle.requiresFullReplacement(cycle);
        if (fullReplacement) {
            validateCompleteRecalculation(request);
            List<DemandScoring> previousItems = scoring.findByPlanningCycle_IdOrderByCriterionOrderIndexAsc(cycle.getId());
            scoring.deleteAll(previousItems);
            scoring.flush();
        }

        for (var item : request.items()) {
            ScoringCriterion criterion = criteria.requireActive(item.criterionId());
            if (item.score().compareTo(criterion.getMinScore()) < 0 || item.score().compareTo(criterion.getMaxScore()) > 0) {
                throw new BadRequestException("Score fora do intervalo permitido para o critério " + criterion.getLabel());
            }

            DemandScoring entity = scoring.findByPlanningCycle_IdAndCriterion_Id(cycle.getId(), criterion.getId())
                    .orElseGet(DemandScoring::new);
            entity.setPlanningCycle(cycle);
            entity.setCriterion(criterion);
            entity.setScore(item.score());
            entity.setWeightedScore(ZERO);
            entity.setNotes(item.notes());
            entity.setScoredBy(actor);
            scoring.save(entity);
        }

        List<DemandScoring> recalculatedItems = recalculateCycleTotals(cycle);
        scoreLifecycle.markCalculated(cycle);
        updateStatusAfterScoring(cycle, recalculatedItems, actor);
        cycles.save(cycle);

        return mapResponse(cycle, recalculatedItems);
    }

    private void validateCompleteRecalculation(UpsertRequest request) {
        Set<UUID> activeCriteriaIds = criteria.findActive().stream()
                .map(ScoringCriterion::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> requestedCriteriaIds = request.items().stream()
                .map(item -> item.criterionId())
                .collect(java.util.stream.Collectors.toSet());
        boolean hasDuplicates = requestedCriteriaIds.size() != request.items().size();
        if (hasDuplicates || !requestedCriteriaIds.equals(activeCriteriaIds)) {
            Set<UUID> missingCriteriaIds = new HashSet<>(activeCriteriaIds);
            missingCriteriaIds.removeAll(requestedCriteriaIds);
            throw new BadRequestException(
                    "O recálculo de um score invalidado deve substituir todos os critérios ativos. "
                            + "Critérios em falta: " + missingCriteriaIds);
        }
    }

    private List<DemandScoring> recalculateCycleTotals(DemandPlanningCycle cycle) {
        List<DemandScoring> items = scoring.findByPlanningCycle_IdOrderByCriterionOrderIndexAsc(cycle.getId());
        if (items.isEmpty()) {
            cycle.setScoreTotal(null);
            cycle.setPortfolioRank(null);
            cycle.setDirectionRank(null);
            recomputeRanks(cycle.getStrategicPlan().getId());
            return items;
        }

        Map<String, BigDecimal> totalsByCode = new LinkedHashMap<>();
        Map<UUID, List<DemandScoring>> itemsByDimension = new LinkedHashMap<>();
        Map<UUID, BigDecimal> rawWeightByDimension = new LinkedHashMap<>();
        Map<UUID, ScoringImpactType> impactByDimension = new LinkedHashMap<>();

        for (DemandScoring item : items) {
            ScoringDimension dimension = item.getCriterion().getDimension();
            if (dimension == null || dimension.getId() == null) {
                item.setWeightedScore(ZERO);
                continue;
            }
            itemsByDimension.computeIfAbsent(dimension.getId(), key -> new java.util.ArrayList<>()).add(item);
            rawWeightByDimension.putIfAbsent(dimension.getId(), safeWeight(dimension.getWeight()));
                impactByDimension.putIfAbsent(dimension.getId(),
                    dimension.getImpactType() == null ? ScoringImpactType.BENEFIT : dimension.getImpactType());
        }

        BigDecimal totalRawWeight = rawWeightByDimension.values().stream()
                .reduce(ZERO, BigDecimal::add);
            BigDecimal rawTotal = ZERO;
            BigDecimal penaltyWeightTotal = ZERO;

        for (Map.Entry<UUID, List<DemandScoring>> entry : itemsByDimension.entrySet()) {
            UUID dimensionId = entry.getKey();
            List<DemandScoring> dimensionItems = entry.getValue();
            if (dimensionItems.isEmpty()) {
                continue;
            }

            DemandScoring first = dimensionItems.get(0);
            ScoringDimension dimension = first.getCriterion().getDimension();
            BigDecimal dimensionWeight = rawWeightByDimension.getOrDefault(dimensionId, ZERO);
            BigDecimal normalizedWeight = totalRawWeight.compareTo(ZERO) > 0
                    ? dimensionWeight.divide(totalRawWeight, 10, RoundingMode.HALF_UP)
                    : ZERO;
                ScoringImpactType impactType = impactByDimension.getOrDefault(dimensionId, ScoringImpactType.BENEFIT);
                boolean isPenalty = impactType == ScoringImpactType.PENALTY;

            BigDecimal normalizedSum = ZERO;
            for (DemandScoring item : dimensionItems) {
                normalizedSum = normalizedSum.add(normalizedScore(item.getCriterion(), item.getScore()));
            }

            BigDecimal normalizedAvg = normalizedSum.divide(
                    BigDecimal.valueOf(dimensionItems.size()),
                    10,
                    RoundingMode.HALF_UP);

            BigDecimal dimensionScore = normalizedAvg
                    .multiply(normalizedWeight)
                    .multiply(ONE_HUNDRED)
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal signedDimensionScore = isPenalty ? dimensionScore.negate() : dimensionScore;

            BigDecimal criteriaCount = BigDecimal.valueOf(dimensionItems.size());
            for (DemandScoring item : dimensionItems) {
                BigDecimal normalizedItem = normalizedScore(item.getCriterion(), item.getScore());
                BigDecimal weightedItem = normalizedItem
                        .multiply(normalizedWeight)
                        .multiply(ONE_HUNDRED)
                        .divide(criteriaCount, 4, RoundingMode.HALF_UP);
                item.setWeightedScore(isPenalty ? weightedItem.negate() : weightedItem);
            }

            String dimensionCode = dimension != null ? dimension.getCode() : null;
            if (dimensionCode != null && !dimensionCode.isBlank()) {
                String normalizedCode = dimensionCode.toUpperCase(Locale.ROOT);
                totalsByCode.merge(normalizedCode, signedDimensionScore, BigDecimal::add);
            }
            rawTotal = rawTotal.add(signedDimensionScore);
            if (isPenalty) {
                penaltyWeightTotal = penaltyWeightTotal.add(normalizedWeight.multiply(ONE_HUNDRED));
            }
        }

        if (!items.isEmpty()) {
            scoring.saveAll(items);
        }

        BigDecimal total = rawTotal.add(penaltyWeightTotal).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(ZERO) < 0) {
            total = ZERO;
        } else if (total.compareTo(ONE_HUNDRED) > 0) {
            total = ONE_HUNDRED;
        }

        cycle.setScoreTotal(total);
        recomputeRanks(cycle.getStrategicPlan().getId());
        return items;
    }

    public void recomputeRanks(UUID strategicPlanId) {
        Specification<DemandPlanningCycle> scoredSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                cb.isNotNull(root.get("scoreTotal")));

        List<DemandPlanningCycle> ranked = cycles.findAll(scoredSpec, Sort.by(
                Sort.Order.desc("scoreTotal"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id")));

        assignRanks(ranked);

        if (!ranked.isEmpty()) {
            cycles.saveAll(ranked);
        }

        Specification<DemandPlanningCycle> noScoreWithRankSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                cb.isNull(root.get("scoreTotal")),
                cb.or(
                        cb.isNotNull(root.get("portfolioRank")),
                cb.isNotNull(root.get("directionRank")),
                cb.isNotNull(root.get("committeeRank"))));

        List<DemandPlanningCycle> noScoreCycles = cycles.findAll(noScoreWithRankSpec);
        for (DemandPlanningCycle noScoreCycle : noScoreCycles) {
            noScoreCycle.setPortfolioRank(null);
            noScoreCycle.setDirectionRank(null);
            noScoreCycle.setCommitteeRank(null);
        }
        if (!noScoreCycles.isEmpty()) {
            cycles.saveAll(noScoreCycles);
        }
    }

    void assignRanks(List<DemandPlanningCycle> rankedCycles) {
        Map<String, Integer> nextRankByDirection = new HashMap<>();
        Map<UUID, Integer> nextRankByCommittee = new HashMap<>();
        int portfolioRank = 1;

        for (DemandPlanningCycle rankedCycle : rankedCycles) {
            rankedCycle.setPortfolioRank(portfolioRank++);
            String directionKey = normalizeDirectionCode(rankedCycle.getDemand().getDirectionCode());
            rankedCycle.setDirectionRank(directionKey == null
                    ? null
                    : nextRankByDirection.merge(directionKey, 1, Integer::sum));
            UUID committeeId = rankedCycle.getResponsibleCommittee() == null ? null
                    : rankedCycle.getResponsibleCommittee().getId();
            rankedCycle.setCommitteeRank(committeeId == null
                    ? null
                    : nextRankByCommittee.merge(committeeId, 1, Integer::sum));
        }
    }

    private String normalizeDirectionCode(String directionCode) {
        if (directionCode == null || directionCode.isBlank()) {
            return null;
        }
        return directionCode.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizedScore(ScoringCriterion criterion, BigDecimal score) {
        BigDecimal min = criterion.getMinScore();
        BigDecimal max = criterion.getMaxScore();
        if (min == null || max == null || score == null) {
            return ZERO;
        }
        BigDecimal range = max.subtract(min);
        if (range.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        BigDecimal normalized = score.subtract(min).divide(range, 10, RoundingMode.HALF_UP);
        if (normalized.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (normalized.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return normalized;
    }

    private BigDecimal safeWeight(BigDecimal weight) {
        return weight == null || weight.compareTo(ZERO) < 0 ? ZERO : weight;
    }

    private void updateStatusAfterScoring(DemandPlanningCycle cycle, List<DemandScoring> items, String actorId) {
        if (!AUTO_STATUS_SCOPE.contains(cycle.getStatus())) {
            return;
        }
        if (cycle.getStatus() == UNDER_ANALYSIS) {
            planningCycleService.transitionStatus(cycle, PRIORITIZATION, "Scoring atualizado", actorId);
        }
    }

    private DemandScoringResponse mapResponse(DemandPlanningCycle cycle, List<DemandScoring> items) {
        class DimensionAccumulator {
            private final UUID dimensionId;
            private final String code;
            private final String label;
            private final BigDecimal weight;
            private BigDecimal score = BigDecimal.ZERO;
            private final java.util.ArrayList<ScoringItemResponse> items = new java.util.ArrayList<>();

            private DimensionAccumulator(UUID dimensionId, String code, String label, BigDecimal weight) {
            this.dimensionId = dimensionId;
            this.code = code;
            this.label = label;
            this.weight = weight;
            }
        }

        Map<UUID, DimensionAccumulator> totalsByDimension = new LinkedHashMap<>();

        for (DemandScoring item : items) {
            ScoringDimension dimension = item.getCriterion().getDimension();
            UUID dimensionId = dimension != null ? dimension.getId() : null;
            if (dimensionId == null) {
            continue;
            }

            String code = dimension.getCode();
            String label = dimension.getLabel();
            BigDecimal weight = dimension.getWeight();
            BigDecimal weighted = item.getWeightedScore() != null ? item.getWeightedScore() : BigDecimal.ZERO;

            DimensionAccumulator acc = totalsByDimension.computeIfAbsent(
                dimensionId,
                id -> new DimensionAccumulator(id, code, label, weight));

            acc.score = acc.score.add(weighted);
            acc.items.add(new ScoringItemResponse(
                item.getId(),
                item.getCriterion().getId(),
                item.getCriterion().getLabel(),
                dimensionId,
                code,
                label,
                weight,
                item.getCriterion().getMinScore(),
                item.getCriterion().getMaxScore(),
                item.getScore(),
                item.getWeightedScore(),
                item.getNotes(),
                item.getScoredAt(),
                item.getScoredBy()));
        }

        List<DimensionScoreResponse> dimensionTotals = totalsByDimension.values().stream()
            .map(acc -> new DimensionScoreResponse(
                acc.dimensionId,
                acc.code,
                acc.label,
                acc.weight,
                acc.score,
                List.copyOf(acc.items)))
            .toList();

        return new DemandScoringResponse(
            cycle.getDemand().getId(),
            cycle.getScoreTotal(),
            cycle.getScoreStatus(),
            cycle.getScoreCalculatedAt(),
            cycle.getScoreInvalidatedAt(),
            cycle.getScoreInvalidationReason(),
            scoreLifecycle.previousScoreSnapshot(cycle),
            dimensionTotals);
    }
}
