package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.ComplexityCriterionConfig;
import cvt.cv.ppmbackend.entity.ComplexityLevelConfig;
import cvt.cv.ppmbackend.enums.ComplexityCriterion;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ComplexityConfigDtos {
    private ComplexityConfigDtos() {
    }

    public record CriterionConfigRequest(
            @NotNull ComplexityCriterion criterion,
            @NotNull @PositiveOrZero Integer lowMin,
            @NotNull @PositiveOrZero Integer lowMax,
            @NotNull @PositiveOrZero Integer mediumMin,
            @NotNull @PositiveOrZero Integer mediumMax,
            @NotNull @PositiveOrZero Integer highMin,
            @NotNull @PositiveOrZero Integer highMax,
            @NotNull @PositiveOrZero Integer veryHighMin,
            @PositiveOrZero Integer veryHighMax,
            @NotNull Boolean active) {
    }

    public record CriterionConfigsUpdateRequest(
            @NotEmpty List<@Valid CriterionConfigRequest> criteria) {
    }

    public record CriterionConfigResponse(
            UUID id,
            ComplexityCriterion criterion,
            Integer lowMin,
            Integer lowMax,
            Integer mediumMin,
            Integer mediumMax,
            Integer highMin,
            Integer highMax,
            Integer veryHighMin,
            Integer veryHighMax,
            boolean active) {
        public static CriterionConfigResponse from(ComplexityCriterionConfig config) {
            return new CriterionConfigResponse(config.getId(), config.getCriterion(), config.getLowMin(),
                    config.getLowMax(), config.getMediumMin(), config.getMediumMax(), config.getHighMin(),
                    config.getHighMax(), config.getVeryHighMin(), config.getVeryHighMax(), config.isActive());
        }
    }

    public record LevelConfigRequest(
            @NotNull DemandComplexity level,
            @NotNull @Positive BigDecimal minScore,
            @NotNull @Positive BigDecimal maxScore,
            @NotNull @Positive Integer estimatedDurationMonths) {
    }

    public record LevelConfigsUpdateRequest(
            @NotEmpty List<@Valid LevelConfigRequest> levels) {
    }

    public record LevelConfigResponse(
            DemandComplexity level,
            BigDecimal minScore,
            BigDecimal maxScore,
            Integer estimatedDurationMonths) {
        public static LevelConfigResponse from(ComplexityLevelConfig config) {
            return new LevelConfigResponse(config.getLevel(), config.getMinScore(), config.getMaxScore(),
                    config.getEstimatedDurationMonths());
        }
    }

    public record DemandComplexityResponse(
            UUID demandId,
            Integer directionsCount,
            Integer profilesCount,
            Integer totalResources,
            Integer dependenciesCount,
            BigDecimal complexityScore,
            DemandComplexity complexity,
            Integer estimatedDurationMonths,
            LocalDate desiredDate,
            LocalDate plannedStartDate) {
    }
}
