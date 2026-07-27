package cvt.cv.ppmbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DemandScoringDtos {
    private DemandScoringDtos() {
    }

    public record ScoreInput(
            @NotNull UUID criterionId,
            @NotNull BigDecimal score,
            @Size(max = 10000) String notes) {
    }

    public record UpsertRequest(@NotEmpty @Valid List<ScoreInput> items) {
    }

    public record ScoringItemResponse(
            UUID id,
            UUID criterionId,
            String criterionLabel,
            UUID dimensionId,
            String dimension,
            String dimensionLabel,
            BigDecimal dimensionWeight,
            BigDecimal minScore,
            BigDecimal maxScore,
            BigDecimal score,
            BigDecimal weightedScore,
            String notes,
            Instant scoredAt,
            String scoredBy) {
    }

    public record DimensionScoreResponse(
            UUID dimensionId,
            String code,
            String label,
            BigDecimal weight,
            BigDecimal score) {
    }

    public record DemandScoringResponse(
            UUID demandId,
            BigDecimal scoreValue,
            BigDecimal scoreEffort,
            BigDecimal scoreRisk,
            BigDecimal scoreTotal,
            List<DimensionScoreResponse> dimensionTotals,
            List<ScoringItemResponse> items) {
    }
}
