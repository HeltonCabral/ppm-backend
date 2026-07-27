package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandScoringDtos.DemandScoringResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.DimensionScoreResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.ScoringItemResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.UpsertRequest;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandScoring;
import cvt.cv.ppmbackend.entity.ScoringCriterion;
import cvt.cv.ppmbackend.entity.ScoringDimension;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.DemandScoringRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DemandScoringService {
    private final DemandRepository demands;
    private final DemandScoringRepository scoring;
    private final ScoringCriterionService criteria;

    public DemandScoringService(DemandRepository demands, DemandScoringRepository scoring,
            ScoringCriterionService criteria) {
        this.demands = demands;
        this.scoring = scoring;
        this.criteria = criteria;
    }

    @Transactional(readOnly = true)
    public DemandScoringResponse getByDemand(UUID demandId) {
        Demand demand = demand(demandId);
        return mapResponse(demand, scoring.findByDemandIdOrderByCriterionOrderIndexAsc(demandId));
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
                BigDecimal dimensionWeight = criterion.getDimension() != null && criterion.getDimension().getWeight() != null
                    ? criterion.getDimension().getWeight()
                    : BigDecimal.ZERO;
                entity.setWeightedScore(item.score().multiply(dimensionWeight));
            entity.setNotes(item.notes());
            entity.setScoredBy(actor);
            scoring.save(entity);
        }

        recalculateDemandTotals(demand);
        demands.save(demand);

        return mapResponse(demand, scoring.findByDemandIdOrderByCriterionOrderIndexAsc(demandId));
    }

    private Demand demand(UUID id) {
        return demands.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demanda não encontrada: " + id));
    }

    private void recalculateDemandTotals(Demand demand) {
        List<DemandScoring> items = scoring.findByDemandIdOrderByCriterionOrderIndexAsc(demand.getId());
        Map<String, BigDecimal> totalsByCode = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (DemandScoring item : items) {
            BigDecimal weighted = item.getWeightedScore() == null ? BigDecimal.ZERO : item.getWeightedScore();
            String dimensionCode = item.getCriterion().getDimension() != null
                    ? item.getCriterion().getDimension().getCode()
                    : null;
            if (dimensionCode != null && !dimensionCode.isBlank()) {
                String normalized = dimensionCode.toUpperCase(Locale.ROOT);
                totalsByCode.merge(normalized, weighted, BigDecimal::add);
            }
            total = total.add(weighted);
        }

        demand.setScoreValue(totalsByCode.getOrDefault("VALUE", BigDecimal.ZERO));
        demand.setScoreEffort(totalsByCode.getOrDefault("EFFORT", BigDecimal.ZERO));
        demand.setScoreRisk(totalsByCode.getOrDefault("RISK", BigDecimal.ZERO));
        demand.setScoreTotal(total);
    }

    private DemandScoringResponse mapResponse(Demand demand, List<DemandScoring> items) {
        Map<UUID, DimensionScoreResponse> totalsByDimension = new LinkedHashMap<>();

        List<ScoringItemResponse> mapped = items.stream()
                .map(item -> {
                    ScoringDimension dimension = item.getCriterion().getDimension();
                    UUID dimensionId = dimension != null ? dimension.getId() : null;
                    String code = dimension != null ? dimension.getCode() : null;
                    String label = dimension != null ? dimension.getLabel() : null;
                    BigDecimal weight = dimension != null ? dimension.getWeight() : null;
                    BigDecimal weighted = item.getWeightedScore() != null ? item.getWeightedScore() : BigDecimal.ZERO;

                    if (dimensionId != null) {
                        DimensionScoreResponse current = totalsByDimension.get(dimensionId);
                        if (current == null) {
                            totalsByDimension.put(dimensionId,
                                    new DimensionScoreResponse(dimensionId, code, label, weight, weighted));
                        } else {
                            totalsByDimension.put(dimensionId,
                                    new DimensionScoreResponse(current.dimensionId(), current.code(),
                                            current.label(), current.weight(), current.score().add(weighted)));
                        }
                    }

                    return new ScoringItemResponse(
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
                            item.getScoredBy());
                })
                .toList();

        return new DemandScoringResponse(
                demand.getId(),
                demand.getScoreValue(),
                demand.getScoreEffort(),
                demand.getScoreRisk(),
                demand.getScoreTotal(),
                List.copyOf(totalsByDimension.values()),
                mapped);
    }
}
