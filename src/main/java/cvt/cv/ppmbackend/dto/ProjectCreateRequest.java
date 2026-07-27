package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectCreateRequest(@NotBlank @Size(max = 180) String name, @Size(max = 10000) String description,
                @NotNull UUID programId,
                UUID operationalPlanId,
                UUID domainId, @NotBlank @Size(max = 120) String businessArea, UUID projectTypeId,
                @Size(max = 120) String responsibleDirection,
                @Size(max = 120) String responsibleTeam, @NotBlank @Size(max = 120) String projectManager,
                @NotNull ProjectStatus status, UUID projectPhaseId,
                UUID mainSupplierId, @Size(max = 150) String impactedSystem, @NotNull ExecutiveStatus scheduleStatus,
                @NotNull ExecutiveStatus costStatus,
                @NotNull ExecutiveStatus riskStatus, @NotNull ExecutiveStatus valueStatus,
                @Size(max = 10000) String expectedBenefits, LocalDate plannedStartDate,
                LocalDate startDate, LocalDate plannedEndDate, LocalDate endDate, @NotNull Priority priority,
                @PositiveOrZero Integer ranking,
                @Size(max = 120) String budgetLine, @PositiveOrZero BigDecimal budget, PlanType planType,
                @Size(max = 10000) String delayReasons) {
}
