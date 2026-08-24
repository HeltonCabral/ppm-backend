package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
public class DemandScoreLifecycleService {
    public static final String SCORE_NOT_CALCULATED = "Não Calculado";
    public static final String SCORE_VALID = "Válido";
    public static final String SCORE_OUTDATED = "Desatualizado";
    public static final String SCORE_INVALID = "Invalidado";

    private static final Set<DemandPlanningStatus> SCORING_ELIGIBLE_STATUSES = Set.of(
            UNDER_ANALYSIS, PRIORITIZATION, READY_FOR_COMMITTEE);
    private static final Map<DemandPlanningStatus, Integer> WORKFLOW_ORDER = Map.of(
            UNDER_ANALYSIS, 0,
            PRIORITIZATION, 1,
            READY_FOR_COMMITTEE, 2);

    private final ObjectMapper objectMapper;

    public DemandScoreLifecycleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateCanScore(DemandPlanningCycle cycle) {
        DemandPlanningStatus status = cycle.getStatus();
        if (status == APPROVED || status == CONDITIONALLY_APPROVED) {
            throw domain("APPROVED_DEMAND_SCORE_RECALCULATION_NOT_ALLOWED",
                    "Uma demanda aprovada não pode ter o score recalculado", status);
        }
        if (!SCORING_ELIGIBLE_STATUSES.contains(status)) {
            throw domain("DEMAND_NOT_ELIGIBLE_FOR_SCORING",
                    "A demanda não está elegível para scoring no estado atual", status);
        }
    }

    public void validateCanApprove(DemandPlanningCycle cycle) {
        if (!isValid(cycle)) {
            throw domain("APPROVAL_REQUIRES_VALID_SCORE",
                    "A demanda só pode ser aprovada com um score válido", cycle.getStatus());
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
        if (to == APPROVED || to == CONDITIONALLY_APPROVED) {
            validateCanApprove(cycle);
            return;
        }
        if (to == BACKLOG) {
            invalidate(cycle, SCORE_INVALID, reasonOrDefault(reason, "Demanda movida para backlog"));
            return;
        }
        if (to == CLOSED) {
            invalidate(cycle, SCORE_INVALID, reasonOrDefault(reason, "Ciclo de planeamento encerrado"));
            return;
        }
        if (to == CONVERTED_TO_PROJECT || to == CONVERTED_TO_PROGRAM) {
            invalidate(cycle, SCORE_INVALID, "Demanda convertida");
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

        if (cycle.getPreviousScoreSnapshot() == null && cycle.getScoreTotal() != null) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scoreTotal", cycle.getScoreTotal());
            snapshot.put("portfolioRank", cycle.getPortfolioRank());
            snapshot.put("directionRank", cycle.getDirectionRank());
            snapshot.put("committeeRank", cycle.getCommitteeRank());
            snapshot.put("scoreStatus", cycle.getScoreStatus());
            snapshot.put("scoreCalculatedAt", cycle.getScoreCalculatedAt());
            try {
                cycle.setPreviousScoreSnapshot(objectMapper.writeValueAsString(snapshot));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Não foi possível persistir o snapshot do score", e);
            }
        }

        cycle.setScoreTotal(null);
        cycle.setPortfolioRank(null);
        cycle.setDirectionRank(null);
        cycle.setCommitteeRank(null);
        cycle.setScoreInvalidatedAt(Instant.now());
        cycle.setScoreInvalidationReason(reason);
        cycle.setScoreStatus(targetScoreStatus);
    }

    private String reasonOrDefault(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private DomainException domain(String code, String message, DemandPlanningStatus status) {
        return new DomainException(HttpStatus.CONFLICT, code, message,
                Map.of("status", status == null ? "" : status.name()));
    }
}
