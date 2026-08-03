package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record OperationalPlanCreateRequest(
                @NotBlank @Size(max = 150) String name,
                @NotNull @Min(2000) @Max(2200) Integer fiscalYear,
                @NotNull UUID strategicPlanId,
                @DecimalMin("0") BigDecimal approvedBudget,
                @Size(max = 10000) String description) {
}
