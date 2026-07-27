package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public record ProgramCreateRequest(@NotBlank @Size(max = 150) String name, @Size(max = 10000) String description,
                @NotBlank @Size(max = 120) String programManager, @NotEmpty Set<UUID> strategicObjectiveIds) {
}
