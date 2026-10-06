package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos.ScoreHistoryEntry;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandPlanningCycleScoreHistory;
import cvt.cv.ppmbackend.entity.DemandScoring;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleScoreHistoryRepository;
import cvt.cv.ppmbackend.repository.DemandScoringRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
public class DemandScoreLifecycleService {
    public static final String SCORE_NOT_CALCULATED = "Não Calculado";
    public static final String SCORE_VALID = "Válido";
    public static final String SCORE_OUTDATED = "Desatualizado";
    public static final String SCORE_INVALID = "Invalidado";

    private static final Set<DemandPlanningStatus> SCORING_ELIGIBLE_STATUSES = Set.of(
            IN_ANALYSIS, IN_PRIORITIZATION, PRIORITIZED);
    private static final Map<DemandPlanningStatus, Integer> WORKFLOW_ORDER = Map.of(
            IN_ANALYSIS, 0,
            IN_PRIORITIZATION, 1,
            PRIORITIZED, 2);

    private final ObjectMapper objectMapper;
    private final DemandScoringRepository scoring;
    private final DemandPlanningCycleScoreHistoryRepository scoreHistory;

    public DemandScoreLifecycleService(ObjectMapper objectMapper,
            DemandScoringRepository scoring,
            DemandPlanningCycleScoreHistoryRepository scoreHistory) {
        this.objectMapper = objectMapper;
        this.scoring = scoring;
        this.scoreHistory = scoreHistory;
    }

    public void validateCanScore(DemandPlanningCycle cycle) {
        DemandPlanningStatus status = cycle.getStatus();
        if (status == IN_PLAN) {
            throw domain("IN_PLAN_DEMAND_SCORE_RECALCULATION_NOT_ALLOWED",
                    "Uma Demanda já incluída no plano não pode ter o score recalculado", status);
        }
        if (!SCORING_ELIGIBLE_STATUSES.contains(status)) {
            throw domain("DEMAND_NOT_ELIGIBLE_FOR_SCORING",
                    "A demanda não está elegível para scoring no estado atual", status);
        }
    }

    public void validateCanEnterPlan(DemandPlanningCycle cycle) {
        if (!isValid(cycle)) {
            throw domain("IN_PLAN_REQUIRES_VALID_SCORE",
                    "A Demanda só pode entrar no plano com um score válido", cycle.getStatus());
        }
    }

    public void markCalculated(DemandPlanningCycle cycle) {
        cycle.setScoreStatus(SCORE_VALID);
        cycle.setScoreCalculatedAt(Instant.now());
        cycle.setScoreInvalidatedAt(null);
        cycle.setScoreInvalidationReason(null);
        cycle.setPreviousScoreSnapshot(null);
    }

    public void applyStatusTransition(DemandPlanningCycle cycle, DemandPlanningStatus from, DemandPlanningStatus to,
            String reason) {
        if (to == IN_PLAN) {
            validateCanEnterPlan(cycle);
            return;
        }
        if (to == BACKLOG) {
            invalidate(cycle, SCORE_INVALID, reasonOrDefault(reason, "Demanda movida para backlog"));
            return;
        }
        if (to == IN_REVIEW) {
            invalidate(cycle, SCORE_INVALID, reasonOrDefault(reason, "Score invalidado por pedido de revisão"));
            return;
        }

        Integer fromOrder = WORKFLOW_ORDER.get(from);
        Integer toOrder = WORKFLOW_ORDER.get(to);
        if (fromOrder != null && toOrder != null && toOrder < fromOrder) {
            invalidate(cycle, SCORE_OUTDATED,
                    reasonOrDefault(reason, "Demanda retornou a uma etapa anterior do fluxo operacional"));
        }
    }

    public void invalidateAfterRelevantUpdate(DemandPlanningCycle cycle) {
        invalidate(cycle, SCORE_OUTDATED, "Dados relevantes para o scoring foram alterados");
    }

    public boolean isValid(DemandPlanningCycle cycle) {
        return SCORE_VALID.equals(cycle.getScoreStatus()) && cycle.getScoreTotal() != null;
    }

    public boolean requiresFullReplacement(DemandPlanningCycle cycle) {
        return SCORE_OUTDATED.equals(cycle.getScoreStatus())
                || SCORE_INVALID.equals(cycle.getScoreStatus())
                || cycle.getPreviousScoreSnapshot() != null;
    }

    public JsonNode previousScoreSnapshot(DemandPlanningCycle cycle) {
        String snapshot = cycle.getPreviousScoreSnapshot();
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Snapshot de score persistido é inválido", e);
        }
    }

    private void invalidate(DemandPlanningCycle cycle, String targetScoreStatus, String reason) {
        boolean hasScoreHistory = cycle.getScoreTotal() != null
                || cycle.getScoreCalculatedAt() != null
                || cycle.getPreviousScoreSnapshot() != null;
        if (!hasScoreHistory) {
            return;
        }

        Instant invalidatedAt = Instant.now();
        if (cycle.getPreviousScoreSnapshot() == null && cycle.getScoreTotal() != null) {
            DimensionTotals dimensions = computeDimensionTotals(cycle.getId());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scoreTotal", cycle.getScoreTotal());
            snapshot.put("scoreValue", dimensions.value);
            snapshot.put("scoreEffort", dimensions.effort);
            snapshot.put("scoreRisk", dimensions.risk);
            snapshot.put("portfolioRank", cycle.getPortfolioRank());
            snapshot.put("calculatedAt", cycle.getScoreCalculatedAt());
            try {
                cycle.setPreviousScoreSnapshot(objectMapper.writeValueAsString(snapshot));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Não foi possível persistir o snapshot do score", e);
            }

            DemandPlanningCycleScoreHistory history = new DemandPlanningCycleScoreHistory();
            history.setPlanningCycle(cycle);
            history.setScoreTotal(cycle.getScoreTotal());
            history.setScoreValue(dimensions.value);
            history.setScoreEffort(dimensions.effort);
            history.setScoreRisk(dimensions.risk);
            history.setPortfolioRank(cycle.getPortfolioRank());
            history.setDirectionRank(cycle.getDirectionRank());
            history.setCommitteeRank(cycle.getCommitteeRank());
            history.setCalculatedAt(cycle.getScoreCalculatedAt());
            history.setInvalidatedAt(invalidatedAt);
            history.setInvalidationReason(reason);
            scoreHistory.save(history);
        }

        cycle.setScoreTotal(null);
        cycle.setPortfolioRank(null);
        cycle.setDirectionRank(null);
        cycle.setCommitteeRank(null);
        cycle.setScoreInvalidatedAt(invalidatedAt);
        cycle.setScoreInvalidationReason(reason);
        cycle.setScoreStatus(targetScoreStatus);
    }

    public List<ScoreHistoryEntry> listHistory(UUID cycleId) {
        return scoreHistory.findByPlanningCycle_IdOrderByInvalidatedAtDesc(cycleId).stream()
                .map(h -> new ScoreHistoryEntry(h.getScoreTotal(), h.getScoreValue(), h.getScoreEffort(),
                        h.getScoreRisk(), h.getPortfolioRank(), h.getCalculatedAt(),
                        h.getInvalidatedAt(), h.getInvalidationReason()))
                .toList();
    }

    private DimensionTotals computeDimensionTotals(UUID cycleId) {
        BigDecimal value = null;
        BigDecimal effort = null;
        BigDecimal risk = null;
        List<DemandScoring> items = scoring.findByPlanningCycle_IdOrderByCriterionOrderIndexAsc(cycleId);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (DemandScoring item : items) {
            var dimension = item.getCriterion() == null ? null : item.getCriterion().getDimension();
            String code = dimension == null ? null : dimension.getCode();
            if (code == null || code.isBlank() || item.getWeightedScore() == null) {
                continue;
            }
            String key = code.trim().toUpperCase(Locale.ROOT);
            totals.merge(key, item.getWeightedScore(), BigDecimal::add);
        }
        if (totals.containsKey("VALUE")) value = totals.get("VALUE");
        if (totals.containsKey("EFFORT")) effort = totals.get("EFFORT");
        if (totals.containsKey("RISK")) risk = totals.get("RISK");
        return new DimensionTotals(value, effort, risk);
    }

    private record DimensionTotals(BigDecimal value, BigDecimal effort, BigDecimal risk) {
    }

    private String reasonOrDefault(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private DomainException domain(String code, String message, DemandPlanningStatus status) {
        return new DomainException(HttpStatus.CONFLICT, code, message,
                Map.of("status", status == null ? "" : status.name()));
    }
}
