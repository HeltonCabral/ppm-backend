package cvt.cv.ppmbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StrategicObjectiveCreateRequest(@NotBlank @Size(max = 150) String name,
        @Size(max = 10000) String description,
        @NotNull @Min(2000) @Max(2200) Integer fiscalYear,
        @Min(2000) @Max(2200) Integer startYear,
        @Min(2000) @Max(2200) Integer endYear,
        @Size(max = 100) String perspective,
        @NotNull UUID strategicElementId,
        UUID strategicPlanId,
        @Valid List<AnnualTargetRequest> annualTargets,
        @Valid List<KpiRequest> kpis) {

    public record AnnualTargetRequest(@NotNull @Min(2000) @Max(2200) Integer year,
            @NotBlank @Size(max = 200) String targetLabel,
            BigDecimal targetValue,
            @DecimalMin("0") @DecimalMax("1") BigDecimal weight) {
    }

    public record KpiRequest(@NotBlank @Size(max = 150) String name,
            @Size(max = 50) String target,
            Double current,
            Double goal,
            @Valid List<KpiMeasurementRequest> measurements) {
    }

    public record KpiMeasurementRequest(@NotNull @Min(2000) @Max(2200) Integer year,
            BigDecimal current,
            BigDecimal goal) {
    }
}
