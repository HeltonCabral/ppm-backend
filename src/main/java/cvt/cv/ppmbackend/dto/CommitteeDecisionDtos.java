package cvt.cv.ppmbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommitteeDecisionDtos {
    private CommitteeDecisionDtos() {
    }

    public record BulkDeliberationFilters(
            BigDecimal scoreMin,
            BigDecimal scoreMax,
            UUID committeeId,
            @Size(max = 60) String directionCode,
            @Size(max = 40) String priority,
            @Size(max = 40) String urgency,
            @Size(max = 40) String riskLevel) {
    }

    public record BulkDeliberationRequest(
            @Valid @NotNull BulkDeliberationFilters filters,
            @NotBlank @Size(max = 60) String decision,
            @Size(max = 10000) String condition,
            @Size(max = 10000) String justification) {
    }

    public record DemandDecisionInfo(
            UUID id,
            String code,
            String title,
            String previousStatus,
            String newStatus,
            String decision,
            String condition,
            String justification,
            Instant decidedAt,
            String decidedBy) {
    }

    public record BulkDeliberationResponse(
            int totalProcessed,
            int successCount,
            int skipCount,
            List<DemandDecisionInfo> processed,
            List<SkippedDemandInfo> skipped) {
    }

    public record SkippedDemandInfo(
            UUID id,
            String code,
            String title,
            String reason) {
    }
}
