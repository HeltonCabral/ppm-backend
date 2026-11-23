package cvt.cv.ppmbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectBaselineDtos {
    private ProjectBaselineDtos() {
    }

    public record CreateRequest(String reason) {
    }

    public record PhaseSnapshot(UUID phaseId, String name, Integer sequence, LocalDate plannedStartDate,
            LocalDate plannedEndDate, BigDecimal weight) {
    }

    public record MilestoneSnapshot(UUID milestoneId, String name, LocalDate dueDate) {
    }

    public record Response(UUID id, UUID projectId, Integer version, Instant capturedAt, String capturedBy,
            String reason, BigDecimal budget, LocalDate plannedStartDate, LocalDate plannedEndDate,
            List<PhaseSnapshot> phases, List<MilestoneSnapshot> milestones) {
    }
}
