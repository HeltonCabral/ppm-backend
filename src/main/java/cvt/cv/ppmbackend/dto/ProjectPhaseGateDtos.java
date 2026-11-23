package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ProjectPhaseGateStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectPhaseGateDtos {
    private ProjectPhaseGateDtos() {
    }

    public record ChecklistItemRequest(UUID id, String label, Boolean met) {
    }

    public record UpdateRequest(List<ChecklistItemRequest> checklist) {
    }

    public record PassRequest(String justification) {
    }

    public record DecisionRequest(String note) {
    }

    public record ChecklistItem(UUID id, String label, boolean met) {
    }

    public record AutomaticCriterion(String code, String label, boolean met) {
    }

    public record Response(UUID id, UUID projectId, UUID phaseId, String phaseName, ProjectPhaseGateStatus status,
            String justification, String requestedBy, Instant requestedAt, String decidedBy, Instant decidedAt,
            String decisionNote, List<ChecklistItem> checklist, List<AutomaticCriterion> automaticCriteria,
            boolean allMet) {
    }
}
