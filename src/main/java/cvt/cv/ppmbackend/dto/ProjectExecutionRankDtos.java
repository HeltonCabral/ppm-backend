package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ReprioritizationReason;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class ProjectExecutionRankDtos {
    private ProjectExecutionRankDtos() {}

    public record ReprioritizeRequest(@NotNull @Min(1) Integer newPosition,
            @NotNull ReprioritizationReason reason,
            @NotBlank @Size(max = 10000) String justification) {}

    public record ReprioritizeResponse(UUID projectId, Integer previousPosition, Integer newPosition) {}
}
