package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.*;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record DependencyCreateRequest(@NotNull DependencyType type, @NotBlank @Size(max = 10000) String description,
        @NotNull UUID sourceProjectId, UUID targetProjectId, @NotNull DependencyStatus status,
        @NotNull ImpactLevel impactLevel) {
}
