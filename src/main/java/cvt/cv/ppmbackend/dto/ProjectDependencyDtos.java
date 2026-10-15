package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ProjectDependencyType;

import java.time.Instant;
import java.util.UUID;

public final class ProjectDependencyDtos {
    private ProjectDependencyDtos() {
    }

    public record Request(UUID predecessorId, UUID successorId, ProjectDependencyType type,
            UUID milestoneId, String notes) {
    }

    public record Response(UUID id, UUID predecessorId, String predecessorCode, String predecessorName,
            UUID successorId, String successorCode, String successorName, ProjectDependencyType type,
            UUID milestoneId, String milestoneName, String notes, long conflictDays,
            Instant createdAt, String createdBy) {
    }
}
