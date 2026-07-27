package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MacroResourceCreateRequest(@NotBlank @Size(max = 150) String name, @NotNull ResourceType type,
        @NotNull @Positive BigDecimal totalCapacity, @NotNull CapacityUnit capacityUnit) {
}
