package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.RateUnit;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record SupplierCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String country,
        @NotNull @PositiveOrZero BigDecimal ratePerHour,
        @NotNull RateUnit rateUnit,
        UUID currencyId) {
}
