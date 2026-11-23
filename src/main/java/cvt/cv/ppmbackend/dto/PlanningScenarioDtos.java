package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.dto.CapacityDtos.PeriodUtilization;
import cvt.cv.ppmbackend.enums.CapacityStatus;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandScenarioSituation;
import cvt.cv.ppmbackend.enums.InclusionSource;
import cvt.cv.ppmbackend.enums.ScenarioCriterionCode;
import cvt.cv.ppmbackend.enums.ScenarioCriterionOperator;
import cvt.cv.ppmbackend.enums.ScenarioCriterionType;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlanningScenarioDtos {
    private PlanningScenarioDtos() {
    }

    // ---------------------------------------------------------------- cenário

    public record CreateRequest(
            @NotNull UUID strategicPlanId,
            UUID operationalPlanId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 10000) String description) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 10000) String description) {
    }

    public record DuplicateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 10000) String description,
            Boolean copyCriteria,
            Boolean copyItems) {
    }

    public record Response(
            UUID id,
            UUID strategicPlanId,
            String strategicPlanName,
            UUID operationalPlanId,
            String operationalPlanName,
            String name,
            String description,
            ScenarioStatus status,
            int includedCount,
            int excludedCount,
            int criteriaCount,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            String selectedBy,
            Instant selectedAt) {
    }

    // --------------------------------------------------------------- critérios

    public record CriterionRequest(
            @NotNull ScenarioCriterionCode criterionCode,
            @NotNull ScenarioCriterionOperator operator,
            @NotBlank @Size(max = 2000) String value,
            ScenarioCriterionType criterionType,
            Integer displayOrder) {
    }

    public record CriterionResponse(
            UUID id,
            ScenarioCriterionCode criterionCode,
            ScenarioCriterionOperator operator,
            String value,
            ScenarioCriterionType criterionType,
            int displayOrder,
            Instant createdAt) {
    }

    /** Resultado da aplicação dos critérios: proposta de composição, ainda ajustável manualmente. */
    public record ApplyCriteriaResponse(
            UUID scenarioId,
            int eligibleDemands,
            int matchedByCriteria,
            int included,
            int excluded,
            int manualInclusionsPreserved,
            int manualExclusionsPreserved,
            List<CriterionResponse> criteria) {
    }

    public record ApplyCriteriaRequest(
            /** Quando true, descarta ajustes manuais anteriores e recompõe só pelos critérios. */
            Boolean resetManualAdjustments) {
    }

    // ------------------------------------------------------------------ itens

    public record IncludeRequest(
            @NotEmpty List<@NotNull UUID> demandIds,
            @Size(max = 10000) String comment) {
    }

    public record ExcludeRequest(
            @NotEmpty List<@NotNull UUID> demandIds,
            @Size(max = 10000) String comment) {
    }

    public record ReorderRequest(
            @NotNull UUID demandId,
            @NotNull @PositiveOrZero Integer newPosition) {
    }

    public record ReorderResponse(
            UUID scenarioId,
            UUID demandId,
            Integer previousRank,
            Integer newRank,
            List<ScenarioRankInfo> affectedDemands) {
    }

    public record ScenarioRankInfo(
            UUID demandId,
            String code,
            String title,
            Integer previousRank,
            Integer newRank) {
    }

    // ---------------------------------------------------------- pool paginado

    public record ScenarioDemandRow(
            UUID demandId,
            UUID planningCycleId,
            String code,
            String title,
            String requester,
            String directionCode,
            String directionName,
            DemandNature nature,
            String type,
            String domain,
            String strategicPillar,
            String strategicObjective,
            BigDecimal score,
            String scoreStatus,
            Integer portfolioRank,
            Integer scenarioRank,
            DemandComplexity complexity,
            BigDecimal complexityScore,
            String capacityStatus,
            BigDecimal estimatedBudget,
            LocalDate mandatoryDeadline,
            int totalDependencies,
            int blockingDependencies,
            DemandScenarioSituation situation,
            InclusionSource inclusionSource,
            String comment) {
    }

    public record ScenarioDemandPage(
            List<ScenarioDemandRow> items,
            int page,
            int pageSize,
            long totalItems,
            int totalPages,
            SituationCounters counters) {
    }

    public record SituationCounters(int included, int inReview, int excluded, int total) {
    }

    // ---------------------------------------------------------------- análise

    public record AnalysisResponse(
            Response scenario,
            List<CriterionResponse> criteria,
            Totals totals,
            PortfolioMetrics metrics,
            CapacityAnalysis capacity,
            DependencyAnalysis dependencies,
            Distributions distributions) {
    }

    public record Totals(
            int eligibleDemands,
            int included,
            int excluded,
            int inReview) {
    }

    public record PortfolioMetrics(
            BigDecimal averageScore,
            BigDecimal includedScoreSum,
            BigDecimal estimatedCost,
            int directionsRepresented,
            List<String> directions,
            int mandatoryIncluded,
            int mandatoryTotal) {
    }

    public record CapacityAnalysis(
            CapacityStatus overallStatus,
            BigDecimal peakUtilization,
            YearMonth criticalPeriod,
            BigDecimal totalAvailableCapacity,
            BigDecimal totalRequiredLoad,
            int profilesOutOfBounds,
            List<ProfileCapacityInfo> profiles) {
    }

    public record ProfileCapacityInfo(
            UUID profileId,
            String profileName,
            Integer availableCapacity,
            Integer simultaneousDemandCapacity,
            CapacityStatus status,
            BigDecimal peakUtilization,
            YearMonth criticalPeriod,
            BigDecimal requiredLoadAtPeak,
            int simultaneousDemandsAtPeak,
            List<PeriodUtilization> periods) {
    }

    public record DependencyAnalysis(
            int totalDependencies,
            int blockingDependencies,
            int includedDemandsWithBlockingDependencies,
            int blockingDependenciesOnExcludedDemands) {
    }

    public record Distributions(
            Map<String, Integer> byDirection,
            Map<String, Integer> byNature,
            Map<String, Integer> byType,
            Map<String, Integer> byDomain) {
    }

    // --------------------------------------------------------------- comparar

    public record CompareRequest(
            @NotEmpty @Size(min = 2, max = 5) List<@NotNull UUID> scenarioIds) {
    }

    public record CompareResponse(
            List<ScenarioComparisonEntry> scenarios,
            List<UUID> commonDemandIds,
            List<ScenarioExclusiveDemands> exclusiveDemands) {
    }

    public record ScenarioComparisonEntry(
            UUID scenarioId,
            String name,
            ScenarioStatus status,
            int included,
            BigDecimal averageScore,
            BigDecimal estimatedCost,
            CapacityStatus capacityStatus,
            BigDecimal capacityPeakUtilization,
            int mandatoryIncluded,
            int blockingDependencies,
            int directionsRepresented) {
    }

    public record ScenarioExclusiveDemands(
            UUID scenarioId,
            List<UUID> demandIds) {
    }

    // ------------------------------------------------------------- finalizar

    public record FinalizeRequest(
            @Size(max = 10000) String justification) {
    }

    public record FinalizeResponse(
            UUID scenarioId,
            ScenarioStatus status,
            int demandsInPlan,
            int demandsInBacklog,
            int archivedScenarios,
            String selectedBy,
            Instant selectedAt) {
    }
}
