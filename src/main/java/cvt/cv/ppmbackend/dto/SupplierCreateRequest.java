package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierCreateRequest(@NotBlank @Size(max = 150) String name, @Size(max = 150) String contract,
        @PositiveOrZero BigDecimal availableHourPackage, @PositiveOrZero BigDecimal consumedHours,
        LocalDate contractStartDate, LocalDate contractEndDate) {
}
