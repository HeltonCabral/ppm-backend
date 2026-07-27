package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record CapacityAllocationCreateRequest(@NotNull UUID projectId, @NotNull UUID macroResourceId,
        @NotNull YearMonth period,
        @NotNull @PositiveOrZero BigDecimal allocatedCapacity, @NotNull CapacityUnit capacityUnit,
        AllocationStatus allocationStatus) {
}
