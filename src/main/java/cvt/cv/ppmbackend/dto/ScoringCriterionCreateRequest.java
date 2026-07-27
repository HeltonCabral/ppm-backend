package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ScoringCriterionCreateRequest(
        @NotBlank @Size(max = 150) String label,
        @NotNull UUID dimensionId,
        @NotNull BigDecimal minScore,
        @NotNull BigDecimal maxScore,
        @NotNull Integer orderIndex,
        Boolean active) {
}
