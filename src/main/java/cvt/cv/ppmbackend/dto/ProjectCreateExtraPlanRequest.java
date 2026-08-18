package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectCreateExtraPlanRequest(@NotBlank @Size(max = 180) String name,
        @Size(max = 10000) String description, @Size(max = 100) String directionCode,
        @NotBlank @Size(max = 200) String directionName, @Size(max = 100) String areaCode,
        @Size(max = 200) String areaName, @NotBlank @Size(max = 150) String domain, UUID supplierId,
        @Size(max = 150) String impactedSystem, @Size(max = 10000) String expectedBenefits,
        @PositiveOrZero BigDecimal budget, LocalDate plannedStartDate, LocalDate plannedEndDate,
        @NotBlank @Size(max = 10000) String extraPlanJustification) {
}
