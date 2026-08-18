package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ProjectStatus;
import jakarta.validation.constraints.NotNull;

public record ProjectStatusUpdateRequest(@NotNull ProjectStatus status) {
}
