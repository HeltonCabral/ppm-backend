package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.CommitteeActionType;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DemandCommitteeActionDtos {
    private DemandCommitteeActionDtos() {
    }

    public record RequestRevisionRequest(
            @NotBlank @Size(max = 10000) String justification) {
    }

    /**
     * Conclusão da revisão. {@code requiresRepriorization} força o regresso a IN_PRIORITIZATION
     * quando a revisão alterou scoring, complexidade, capacidade ou dependências.
     */
    public record ResolveRevisionRequest(
            @Size(max = 10000) String resolutionNotes,
            Boolean requiresRepriorization) {
    }

    public record Response(
            UUID id,
            UUID planningCycleId,
            UUID demandId,
            UUID committeeId,
            String committeeName,
            CommitteeActionType action,
            String justification,
            String requestedBy,
            Instant requestedAt,
            String resolvedBy,
            Instant resolvedAt,
            String resolutionNotes,
            DemandPlanningStatus previousStatus,
            boolean open,
            Instant createdAt) {
    }
}
