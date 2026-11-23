package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ProjectCostSource;
import cvt.cv.ppmbackend.enums.ProjectCostType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ProjectCostDtos {
    private ProjectCostDtos() {
    }

    public record Request(UUID budgetLineId, String month, ProjectCostType type, BigDecimal planned,
            BigDecimal actual, String description, ProjectCostSource source) {
    }

    public record Response(UUID id, UUID projectId, UUID budgetLineId, String budgetLineLabel, String month,
            ProjectCostType type, BigDecimal planned, BigDecimal actual, String description,
            ProjectCostSource source, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
    }

    public record Financials(BigDecimal totalPlanned, BigDecimal totalActual, BigDecimal eac,
            BigDecimal variance, String lastActualMonth) {
    }
}
