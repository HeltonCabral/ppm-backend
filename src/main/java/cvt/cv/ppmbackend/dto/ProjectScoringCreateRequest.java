package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ProjectScoringCreateRequest(@NotNull UUID projectId, @NotNull @Min(1) @Max(5) Integer strategicAlignment,
        @NotNull @Min(1) @Max(5) Integer roi, @NotNull @Min(1) @Max(5) Integer urgency,
        @NotNull @Min(1) @Max(5) Integer technicalComplexity,
        @NotNull @Min(1) @Max(5) Integer resourceAvailability, @NotNull @Min(1) @Max(5) Integer estimatedDuration,
        @NotNull @Min(1) @Max(5) Integer technologyRisk, @NotNull @Min(1) @Max(5) Integer dependencyRisk,
        @NotNull @Min(1) @Max(5) Integer adoptionRisk) {
}
