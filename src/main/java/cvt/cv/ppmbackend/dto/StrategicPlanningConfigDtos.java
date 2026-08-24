package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.StrategicPlanningConfig;
import cvt.cv.ppmbackend.enums.ReviewFrequency;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class StrategicPlanningConfigDtos {
    private StrategicPlanningConfigDtos() {
    }

    public record UpdateRequest(
            @NotNull Boolean rollingPlan,
            @NotNull @Min(2) Integer horizonYears,
            @NotNull ReviewFrequency reviewFrequency) {
    }

    public record Response(
            UUID id,
            boolean rollingPlan,
            Integer horizonYears,
            ReviewFrequency reviewFrequency,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        public static Response from(StrategicPlanningConfig config) {
            return new Response(config.getId(), config.isRollingPlan(), config.getHorizonYears(),
                    config.getReviewFrequency(), config.isActive(), config.getCreatedAt(), config.getUpdatedAt());
        }
    }
}
