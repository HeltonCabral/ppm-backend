package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record StrategicPillarCreateRequest(@NotBlank @Size(max = 150) String name,
        @Size(max = 10000) String description,
        @Size(max = 100) String icon,
        UUID strategicPlanId) {
}
