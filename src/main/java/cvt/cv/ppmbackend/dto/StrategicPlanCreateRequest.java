package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;

public record StrategicPlanCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull @Min(2000) @Max(2200) Integer startYear,
        @NotNull @Min(2000) @Max(2200) Integer endYear,
        @Size(max = 10000) String description) {
}
