package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.BenefitType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BenefitCreateRequest(@NotNull UUID projectId, @NotNull BenefitType benefitType,
        @PositiveOrZero BigDecimal expectedValue,
        @PositiveOrZero BigDecimal realizedValue, @NotNull LocalDate trackingDate, @Size(max = 10000) String notes) {
}
