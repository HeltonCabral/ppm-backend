package cvt.cv.ppmbackend.enums;

import java.util.Locale;
import java.util.Map;

public enum CommitteeDecision {
    APPROVED,
    CONDITIONALLY_APPROVED,
    REQUEST_REVISION,
    BACKLOG;

    /** Nomes aceites por compatibilidade com os clientes anteriores à tabela de decisões. */
    private static final Map<String, CommitteeDecision> ALIASES = Map.of(
            "CONDITIONALLY_APPROVE", CONDITIONALLY_APPROVED,
            "REVISION_REQUESTED", REQUEST_REVISION);

    public static CommitteeDecision from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        CommitteeDecision alias = ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }
        for (CommitteeDecision decision : values()) {
            if (decision.name().equals(normalized)) {
                return decision;
            }
        }
        return null;
    }
}
