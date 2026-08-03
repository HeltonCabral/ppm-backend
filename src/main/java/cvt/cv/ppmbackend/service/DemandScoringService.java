package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandScoringDtos.DemandScoringResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.DimensionScoreResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.ScoringItemResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.UpsertRequest;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandScoring;
import cvt.cv.ppmbackend.entity.ScoringCriterion;
import cvt.cv.ppmbackend.entity.ScoringDimension;
import cvt.cv.ppmbackend.enums.ScoringImpactType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.DemandScoringRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DemandScoringService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String STATUS_IN_ANALYSYS = "IN_ANALYSYS";
        private static final String STATUS_IN_PRIORIZATION = "IN_PRIORIZATION";
        private static final String STATUS_PRIORITIZED = "PRIORITIZED";
        private static final String STATUS_UNDER_PRIORITIZATION = "UNDER_PRIORITIZATION";
        private static final String STATUS_READY_FOR_COMMITTEE = "READY_FOR_COMMITTEE";
    private static final Set<String> AUTO_STATUS_SCOPE = Set.of(
            STATUS_IN_ANALYSYS,
            STATUS_IN_PRIORIZATION,
            STATUS_PRIORITIZED,
            STATUS_UNDER_PRIORITIZATION,
            STATUS_READY_FOR_COMMITTEE);
    private final DemandRepository demands;
    private final DemandScoringRepository scoring;
    private final ScoringCriterionService criteria;

    public DemandScoringService(DemandRepository demands, DemandScoringRepository scoring,
            ScoringCriterionService criteria) {
        this.demands = demands;
        this.scoring = scoring;
        this.criteria = criteria;
    }

    public DemandScoringResponse getByDemand(UUID demandId) {
        Demand demand = demand(demandId);
        List<DemandScoring> recalculatedItems = recalculateDemandTotals(demand);
        demands.save(demand);
        return mapResponse(demand, recalculatedItems);
    }

    public DemandScoringResponse upsert(UUID demandId, UpsertRequest request, String actorId) {
        Demand demand = demand(demandId);
        String actor = actorId == null || actorId.isBlank() ? "system" : actorId;

        for (var item : request.items()) {
            ScoringCriterion criterion = criteria.requireActive(item.criterionId());
            if (item.score().compareTo(criterion.getMinScore()) < 0 || item.score().compareTo(criterion.getMaxScore()) > 0) {
                throw new BadRequestException("Score fora do intervalo permitido para o critério " + criterion.getLabel());
            }

            DemandScoring entity = scoring.findByDemandIdAndCriterionId(demandId, criterion.getId())
                    .orElseGet(DemandScoring::new);
            entity.setDemand(demand);
            entity.setCriterion(criterion);
            entity.setScore(item.score());
            entity.setWeightedScore(ZERO);
            entity.setNotes(item.notes());
            entity.setScoredBy(actor);
            scoring.save(entity);
        }

        List<DemandScoring> recalculatedItems = recalculateDemandTotals(demand);
        updateStatusAfterScoring(demand, recalculatedItems, actor);
        demands.save(demand);

        return mapResponse(demand, recalculatedItems);
    }

    private Demand demand(UUID id) {
        return demands.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demanda não encontrada: " + id));
    }

    private List<DemandScoring> recalculateDemandTotals(Demand demand) {
        List<DemandScoring> items = scoring.findByDemandIdOrderByCriterionOrderIndexAsc(demand.getId());
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

        demand.setScoreValue(totalsByCode.getOrDefault("VALUE", ZERO).setScale(2, RoundingMode.HALF_UP));
        demand.setScoreEffort(totalsByCode.getOrDefault("EFFORT", ZERO).setScale(2, RoundingMode.HALF_UP));
        demand.setScoreRisk(totalsByCode.getOrDefault("RISK", ZERO).setScale(2, RoundingMode.HALF_UP));
        demand.setScoreTotal(total);
        return items;
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

    private void updateStatusAfterScoring(Demand demand, List<DemandScoring> items, String actor) {
        String currentStatus = normalizeStatus(demand.getStatus());
        if (!AUTO_STATUS_SCOPE.contains(currentStatus)) {
            return;
        }

        long activeCriteriaCount = criteria.findActive().size();
        Set<UUID> scoredCriteria = new HashSet<>();
        for (DemandScoring item : items) {
            if (item.getCriterion() != null && item.getCriterion().getId() != null) {
                scoredCriteria.add(item.getCriterion().getId());
            }
        }

        if (activeCriteriaCount > 0 && scoredCriteria.size() >= activeCriteriaCount) {
            demand.setStatus(STATUS_PRIORITIZED);
            demand.setUpdatedBy(actor);
            return;
        }

        if (STATUS_IN_ANALYSYS.equals(currentStatus)) {
            demand.setStatus(STATUS_IN_PRIORIZATION);
            demand.setUpdatedBy(actor);
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private DemandScoringResponse mapResponse(Demand demand, List<DemandScoring> items) {
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
            demand.getId(),
            demand.getScoreTotal(),
            dimensionTotals);
    }
}
