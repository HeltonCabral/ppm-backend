package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.RiskLevel;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectExecutionUpdateRequest(@NotNull @Min(0) @Max(100) Integer progress,
        @NotNull @PositiveOrZero BigDecimal consumedBudget, LocalDate plannedStartDate, LocalDate actualStartDate,
        LocalDate plannedEndDate, LocalDate actualEndDate, @NotNull ExecutiveStatus scheduleStatus,
        @NotNull ExecutiveStatus costStatus, @NotNull ExecutiveStatus riskStatus,
        @NotNull ExecutiveStatus valueStatus, @NotNull RiskLevel risk, @Size(max = 10000) String delayReasons,
        @Size(max = 10000) String executionNotes) {
}
