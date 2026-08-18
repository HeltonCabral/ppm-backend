package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.enums.RiskLevel;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectUpdateRequest(@NotBlank @Size(max = 180) String name, @Size(max = 10000) String description,
                UUID programId, UUID strategicPlanId, UUID operationalPlanId, UUID strategicPillarId,
                UUID strategicObjectiveId, @Size(max = 100) String directionCode,
                @Size(max = 200) String directionName, @Size(max = 100) String areaCode,
                @Size(max = 200) String areaName, @Size(max = 150) String domain, UUID supplierId,
                @Size(max = 150) String impactedSystem, @Size(max = 10000) String expectedBenefits,
                @PositiveOrZero BigDecimal budget, UUID projectPhaseId,
                @Size(max = 120) String projectManager,
                @Size(max = 10000) String extraPlanJustification,
                // Execution fields
                @Min(0) @Max(100) Integer progress,
                @PositiveOrZero BigDecimal consumedBudget,
                LocalDate plannedStartDate, LocalDate actualStartDate,
                LocalDate plannedEndDate, LocalDate actualEndDate,
                ExecutiveStatus scheduleStatus, ExecutiveStatus costStatus,
                ExecutiveStatus riskStatus, ExecutiveStatus valueStatus,
                RiskLevel risk,
                @Size(max = 10000) String delayReasons,
                @Size(max = 10000) String executionNotes) {
}
