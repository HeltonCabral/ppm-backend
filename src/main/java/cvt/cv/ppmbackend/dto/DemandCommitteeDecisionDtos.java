package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.CommitteeDecision;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DemandCommitteeDecisionDtos {
    private DemandCommitteeDecisionDtos() {
    }

    /** Dados de uma deliberação, comuns a todos os endpoints que registam decisões do Comité. */
    public record DecisionCommand(
            CommitteeDecision decision,
            String justification,
            String conditions,
            String meetingReference,
            String actorId,
            String actorName) {
    }

    public record Response(
            UUID id,
            UUID planningCycleId,
            UUID demandId,
            UUID committeeId,
            String committeeName,
            CommitteeDecision decision,
            String justification,
            String conditions,
            Instant decisionAt,
            String decidedBy,
            String meetingReference,
            Instant createdAt) {
    }

    public record MeetingReference(@Size(max = 200) String meetingReference) {
    }
}
