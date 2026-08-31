package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
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
            @Size(max = 40) String urgency,
            @Size(max = 40) String riskLevel) {
    }

    public record BulkDeliberationRequest(
            @NotNull UUID strategicPlanId,
            @Valid @NotNull BulkDeliberationFilters filters,
            @NotBlank @Size(max = 60) String decision,
            @Size(max = 10000) String condition,
            @Size(max = 10000) String justification,
            @Size(max = 200) String meetingReference) {
    }

    public record DemandDecisionInfo(
            UUID id,
            String code,
            String title,
            DemandPlanningStatus previousStatus,
            DemandPlanningStatus newStatus,
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

    public record DecisionDashboardResponse(
            StrategicPlanDecisionSummary plan,
            ScenarioDecisionSummary scenario,
            DecisionOverview overview,
            List<AttentionPoint> attentionPoints) {
    }

    public record StrategicPlanDecisionSummary(
            UUID id,
            String name,
            Integer startYear,
            Integer endYear,
            String status,
            String statusLabel,
            int deliberatedDemands,
            int totalDemands,
            int deliberationPercentage) {
    }

    public record ScenarioDecisionSummary(
            int demandsCount,
            BigDecimal requiredBudget,
            CapacitySummary capacity,
            int pendingDecisions,
            int openConditions) {
    }

    public record DecisionOverview(
            BigDecimal totalBudget,
            CapacitySummary capacity,
            int deficitProfiles,
            int blockedDemands,
            int criticalDependencies) {
    }

    public record CapacitySummary(
            BigDecimal available,
            BigDecimal required,
            BigDecimal coveragePercentage) {
    }

    public record AttentionPoint(
            String code,
            String severity,
            String message,
            int count) {
    }
}
