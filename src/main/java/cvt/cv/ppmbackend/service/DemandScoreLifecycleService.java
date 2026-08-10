package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DemandScoreLifecycleService {
    public static final String SCORE_NOT_CALCULATED = "N\u00e3o Calculado";
    public static final String SCORE_VALID = "V\u00e1lido";
    public static final String SCORE_OUTDATED = "Desatualizado";
    public static final String SCORE_INVALID = "Invalidado";

    private static final Set<String> SCORING_ELIGIBLE_STATUSES = Set.of(
            "IN_ANALYSIS",
            "IN_PRIORIZATION",
            "PRIORITIZED",
            "UNDER_PRIORITIZATION",
            "READY_FOR_COMMITTEE");
    private static final Map<String, Integer> WORKFLOW_ORDER = Map.of(
            "IN_ANALYSIS", 0,
            "IN_PRIORIZATION", 1,
            "UNDER_PRIORITIZATION", 1,
            "PRIORITIZED", 2,
            "READY_FOR_COMMITTEE", 3);

    private final ObjectMapper objectMapper;

    public DemandScoreLifecycleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateCanScore(Demand demand) {
        String status = normalizeStatus(demand.getStatus());
        if ("APPROVED".equals(status)) {
            throw domain("APPROVED_DEMAND_SCORE_RECALCULATION_NOT_ALLOWED",
                    "Uma demanda aprovada n\u00e3o pode ter o score recalculado", status);
        }
        if (!SCORING_ELIGIBLE_STATUSES.contains(status)) {
            throw domain("DEMAND_NOT_ELIGIBLE_FOR_SCORING",
                    "A demanda n\u00e3o est\u00e1 eleg\u00edvel para scoring no estado atual", status);
        }
    }

    public void validateCanApprove(Demand demand) {
        if (!isValid(demand)) {
            throw domain("APPROVAL_REQUIRES_VALID_SCORE",
                    "A demanda s\u00f3 pode ser aprovada com um score v\u00e1lido", normalizeStatus(demand.getStatus()));
        }
    }

    public void markCalculated(Demand demand) {
        demand.setScoreStatus(SCORE_VALID);
        demand.setScoreCalculatedAt(Instant.now());
        demand.setScoreInvalidatedAt(null);
        demand.setScoreInvalidationReason(null);
        demand.setPreviousScoreSnapshot(null);
    }

    public void applyStatusTransition(Demand demand, String from, String to, String reason) {
        if ("APPROVED".equals(to)) {
            validateCanApprove(demand);
            return;
        }
        if ("REJECTED".equals(to)) {
            invalidate(demand, SCORE_INVALID, reasonOrDefault(reason, "Demanda rejeitada"));
            return;
        }
        if ("ARCHIVED".equals(to)) {
            invalidate(demand, SCORE_INVALID, reasonOrDefault(reason, "Demanda arquivada"));
            return;
        }
        if ("CONVERTED_TO_PROJECT".equals(to)) {
            invalidate(demand, SCORE_INVALID, "Demanda convertida em projeto");
            return;
        }

        Integer fromOrder = WORKFLOW_ORDER.get(normalizeStatus(from));
        Integer toOrder = WORKFLOW_ORDER.get(normalizeStatus(to));
        if (fromOrder != null && toOrder != null && toOrder < fromOrder) {
            invalidate(demand, SCORE_OUTDATED,
                    reasonOrDefault(reason, "Demanda retornou a uma etapa anterior do fluxo operacional"));
        }
    }

    public void invalidateAfterRelevantUpdate(Demand demand) {
        invalidate(demand, SCORE_OUTDATED, "Dados relevantes para o scoring foram alterados");
    }

    public boolean isValid(Demand demand) {
        return SCORE_VALID.equals(demand.getScoreStatus()) && demand.getScoreTotal() != null;
    }

    public boolean requiresFullReplacement(Demand demand) {
        return SCORE_OUTDATED.equals(demand.getScoreStatus())
                || SCORE_INVALID.equals(demand.getScoreStatus())
                || demand.getPreviousScoreSnapshot() != null;
    }

    public JsonNode previousScoreSnapshot(Demand demand) {
        String snapshot = demand.getPreviousScoreSnapshot();
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Snapshot de score persistido \u00e9 inv\u00e1lido", e);
        }
    }

    private void invalidate(Demand demand, String targetScoreStatus, String reason) {
        boolean hasScoreHistory = demand.getScoreTotal() != null
                || demand.getScoreCalculatedAt() != null
                || demand.getPreviousScoreSnapshot() != null;
        if (!hasScoreHistory) {
            return;
        }

        if (demand.getPreviousScoreSnapshot() == null && demand.getScoreTotal() != null) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scoreTotal", demand.getScoreTotal());
            snapshot.put("portfolioRank", demand.getPortfolioRank());
            snapshot.put("directionRank", demand.getDirectionRank());
            snapshot.put("scoreStatus", demand.getScoreStatus());
            snapshot.put("scoreCalculatedAt", demand.getScoreCalculatedAt());
            try {
                demand.setPreviousScoreSnapshot(objectMapper.writeValueAsString(snapshot));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("N\u00e3o foi poss\u00edvel persistir o snapshot do score", e);
            }
        }

        demand.setScoreTotal(null);
        demand.setPortfolioRank(null);
        demand.setDirectionRank(null);
        demand.setScoreInvalidatedAt(Instant.now());
        demand.setScoreInvalidationReason(reason);
        demand.setScoreStatus(targetScoreStatus);
    }

    private String normalizeStatus(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("IN_ANALYSYS".equals(normalized)) {
            return "IN_ANALYSIS";
        }
        if ("IN_PRIORITIZATION".equals(normalized)) {
            return "IN_PRIORIZATION";
        }
        return normalized;
    }

    private String reasonOrDefault(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private DomainException domain(String code, String message, String status) {
        return new DomainException(HttpStatus.CONFLICT, code, message, Map.of("status", status));
    }
}
