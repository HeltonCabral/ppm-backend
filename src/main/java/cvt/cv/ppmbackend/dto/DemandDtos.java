package cvt.cv.ppmbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.ProfileCategory;
import cvt.cv.ppmbackend.enums.PlanType;
import cvt.cv.ppmbackend.enums.Priority;
import cvt.cv.ppmbackend.validation.ValidReprioritizeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.DemandScoringResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DemandDtos {
        private DemandDtos() {
        }

        public record AttachmentInput(
                        @NotBlank @Size(max = 250) String name,
                        @NotBlank @Size(max = 1000) String url,
                        @Size(max = 150) String contentType) {
        }

        public record Create(
                        @NotBlank @Size(max = 250) String title,
                        @Size(max = 10000) String description,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @Size(max = 120) String directionName,
                        @Size(max = 60) String directionCode,
                        DirectionParticipationType directionParticipationType,
                        @Size(max = 150) String sponsor,
                        @NotNull UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        UUID strategicPlanId,
                        UUID operationalPlanId,
                        UUID strategicPillarId,
                        UUID strategicObjectiveId,
                        UUID programId,
                        UUID committeeId,
                        UUID domainId,
                        @Size(max = 150) String impactedSystem,
                        @Size(max = 40) String initialPriority,
                        @Size(max = 40) String estimatedEffort,
                        @Size(max = 10000) String expectedImpact,
                        @Size(max = 10000) String expectedBenefit,
                        @Size(max = 40) String urgency,
                        @PositiveOrZero BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        @Size(max = 10000) String notes,
                        @Size(max = 40) String capacityStatus,
                        @Size(max = 40) String riskStatus,
                        @Size(max = 10000) String risksIdentified,
                        @Size(max = 10000) String dependenciesIdentified,
                        @PositiveOrZero Integer dependenciesCount,

                        BigDecimal scoreTotal,
                        @PositiveOrZero Integer portfolioRank,
                        @PositiveOrZero Integer directionRank,
                        @Size(max = 40) String approvalType,
                        @Size(max = 60) String committeeDecision,
                        @Size(max = 10000) String rejectionReason,
                        @Valid List<AttachmentInput> attachments,
                        @Valid List<ParticipatingDirectionCreate> participatingDirections,
                        @Valid List<DemandProfileRequirementInput> profileRequirements) {
        }

        public record Update(
                        @NotBlank @Size(max = 250) String title,
                        @Size(max = 10000) String description,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @Size(max = 120) String directionName,
                        @Size(max = 60) String directionCode,
                        DirectionParticipationType directionParticipationType,
                        @Size(max = 150) String sponsor,
                        @NotNull UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        UUID strategicPlanId,
                        UUID operationalPlanId,
                        UUID strategicPillarId,
                        UUID strategicObjectiveId,
                        UUID programId,
                        UUID committeeId,
                        UUID domainId,
                        @Size(max = 150) String impactedSystem,
                        @Size(max = 40) String initialPriority,
                        @Size(max = 40) String estimatedEffort,
                        @Size(max = 10000) String expectedImpact,
                        @Size(max = 10000) String expectedBenefit,
                        @Size(max = 40) String urgency,
                        @PositiveOrZero BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        @Size(max = 10000) String notes,
                        @Size(max = 40) String capacityStatus,
                        @Size(max = 40) String riskStatus,
                        @Size(max = 10000) String risksIdentified,
                        @Size(max = 10000) String dependenciesIdentified,
                        @PositiveOrZero Integer dependenciesCount,

                        BigDecimal scoreTotal,
                        @PositiveOrZero Integer portfolioRank,
                        @PositiveOrZero Integer directionRank,
                        @Size(max = 40) String approvalType,
                        @Size(max = 60) String committeeDecision,
                        @Size(max = 10000) String rejectionReason,
                        @Valid List<ParticipatingDirectionCreate> participatingDirections,
                        @Valid List<DemandProfileRequirementInput> profileRequirements) {
        }

        public record Patch(
                        @Size(max = 250) String title,
                        @Size(max = 10000) String description,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @Size(max = 120) String directionName,
                        @Size(max = 60) String directionCode,
                        DirectionParticipationType directionParticipationType,
                        @Size(max = 150) String sponsor,
                        UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        UUID strategicPlanId,
                        UUID operationalPlanId,
                        UUID strategicPillarId,
                        UUID strategicObjectiveId,
                        UUID programId,
                        UUID committeeId,
                        UUID domainId,
                        @Size(max = 150) String impactedSystem,
                        @Size(max = 40) String initialPriority,
                        @Size(max = 40) String estimatedEffort,
                        @Size(max = 10000) String expectedImpact,
                        @Size(max = 10000) String expectedBenefit,
                        @Size(max = 40) String urgency,
                        @PositiveOrZero BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        @Size(max = 10000) String notes,
                        @Size(max = 40) String capacityStatus,
                        @Size(max = 40) String riskStatus,
                        @Size(max = 10000) String risksIdentified,
                        @Size(max = 10000) String dependenciesIdentified,
                        @PositiveOrZero Integer dependenciesCount,

                        BigDecimal scoreTotal,
                        @PositiveOrZero Integer portfolioRank,
                        @PositiveOrZero Integer directionRank,
                        @Size(max = 40) String approvalType,
                        @Size(max = 60) String committeeDecision,
                        @Size(max = 10000) String rejectionReason,
                        @Valid List<ParticipatingDirectionCreate> participatingDirections,
                        @Valid List<DemandProfileRequirementInput> profileRequirements) {
        }

        public record StatusPatch(@NotBlank @Size(max = 50) String status, @Size(max = 4000) String reason) {
        }

        public record AssignCommitteeRequest(@NotNull UUID committeeId) {
        }

        public record SendToStrategicCommitteeRequest(@Size(max = 4000) String reason) {
        }

        public record SendToStrategicCommitteeBulkRequest(
                        @NotEmpty(message = "A lista de demandas é obrigatória.")
                        List<@NotNull(message = "A lista de demandas não pode conter IDs nulos.") UUID> demandIds,
                        @Size(max = 4000) String reason) {
        }

        public record AttachmentCreate(
                        @NotBlank @Size(max = 250) String name,
                        @NotBlank @Size(max = 1000) String url,
                        @Size(max = 150) String contentType) {
        }

        public record ConvertToProject(
                        @Size(max = 180) String projectName,
                        @Size(max = 10000) String description,
                        UUID programId,
                        UUID operationalPlanId,
                        UUID domainId,
                        UUID projectTypeId,
                        @Size(max = 120) String businessArea,
                        @Size(max = 120) String responsibleDirection,
                        @Size(max = 120) String responsibleTeam,
                        UUID managerId,
                        @Size(max = 120) String projectManager,
                        UUID projectPhaseId,
                        UUID mainSupplierId,
                        @Size(max = 150) String impactedSystem,
                        @Size(max = 10000) String expectedBenefits,
                        LocalDate plannedStartDate,
                        LocalDate plannedEndDate,
                        LocalDate startDate,
                        LocalDate endDate,
                        Priority priority,
                        @PositiveOrZero Integer ranking,
                        @Size(max = 120) String budgetLine,
                        @PositiveOrZero BigDecimal budget,
                        PlanType planType,
                        @Size(max = 10000) String delayReasons) {
        }

        public record DemandAttachmentResponse(
                        UUID id,
                        String name,
                        String url,
                        String contentType,
                        Instant createdAt,
                        String createdBy) {
        }

        public record LookupValueSummary(UUID id, String category, String code, String label) {
        }

        public record StrategicPlanSummary(UUID id, String name, Integer startYear, Integer endYear, String status) {
        }

        public record OperationalPlanSummary(UUID id, String name, Integer fiscalYear, String status) {
        }

        public record StrategicPillarSummary(UUID id, String name, String description) {
        }

        public record StrategicObjectiveSummary(UUID id, String name, Integer fiscalYear, Integer startYear,
                        Integer endYear,
                        String perspective) {
        }

        public record ProgramSummary(UUID id, String name, String programManager) {
        }

        public record CommitteeSummary(UUID id, String name, String status, boolean isStrategicCommittee) {
        }

        public record ProjectSummary(UUID id, String name, String status) {
        }

        public record DemandResponse(
                        UUID id,
                        String code,
                        String title,
                        String description,
                        String requester,
                        String areaName,
                        String areaCode,
                        String directionName,
                        String directionCode,
                        DirectionParticipationType directionParticipationType,
                        String sponsor,
                        UUID typeId,
                        String type,
                        String origin,
                        String easyVistaRef,
                        UUID strategicPlanId,
                        UUID operationalPlanId,
                        UUID strategicPillarId,
                        UUID strategicObjectiveId,
                        UUID programId,
                        UUID committeeId,
                        UUID responsibleCommitteeId,
                        UUID domainId,
                        String domain,
                        String impactedSystem,
                        String initialPriority,
                        String estimatedEffort,
                        String expectedImpact,
                        String expectedBenefit,
                        String urgency,
                        BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        String notes,
                        String status,
                        boolean inStrategicCommittee,
                        Instant strategicCommitteeAt,
                        String capacityStatus,
                        String riskStatus,
                        String risksIdentified,
                        String dependenciesIdentified,
                        Integer directionsCount,
                        Integer profilesCount,
                        Integer totalResources,
                        Integer dependenciesCount,
                        BigDecimal complexityScore,
                        DemandComplexity complexity,
                        Integer estimatedDurationMonths,
                        LocalDate plannedStartDate,
                        BigDecimal scoreTotal,
                        String scoreStatus,
                        Instant scoreCalculatedAt,
                        Instant scoreInvalidatedAt,
                        String scoreInvalidationReason,
                        JsonNode previousScoreSnapshot,
                        BigDecimal preScore,
                        String preScoreClassification,
                        Integer portfolioRank,
                        Integer directionRank,
                        Integer committeeRank,
                        String approvalType,
                        String committeeDecision,
                        String rejectionReason,
                        UUID convertedProjectId,
                        Instant createdAt,
                        String createdBy,
                        Instant updatedAt,
                        String updatedBy,
                        Long version,
                        LookupValueSummary typeData,
                        LookupValueSummary domainData,
                        StrategicPlanSummary strategicPlan,
                        OperationalPlanSummary operationalPlan,
                        StrategicPillarSummary strategicPillar,
                        StrategicObjectiveSummary strategicObjective,
                        ProgramSummary program,
                        CommitteeSummary committee,
                        CommitteeSummary responsibleCommittee,
                        ProjectSummary convertedProject,
                        List<DemandAttachmentResponse> attachments,
                        List<ParticipatingDirectionResponse> participatingDirections,
                        List<DemandProfileRequirementResponse> profileRequirements,
                        DemandScoringResponse calculatedScoring) {
        }

        public record DemandHistoryResponse(
                        UUID id,
                        String eventType,
                        String previousStatus,
                        String newStatus,
                        String description,
                        String actorId,
                        String actorName,
                        Instant occurredAt,
                        String metadata) {
        }

        public record PagedDemandsResponse(List<DemandResponse> items, int page, int pageSize, long totalItems,
                        int totalPages) {
        }

        public record ConvertResponse(DemandConvertInfo demand, ProjectConvertInfo project) {
        }

        public record DemandConvertInfo(
                        UUID id,
                        String code,
                        String status,
                        UUID convertedProjectId,
                        String scoreStatus,
                        Instant scoreCalculatedAt,
                        Instant scoreInvalidatedAt,
                        String scoreInvalidationReason,
                        JsonNode previousScoreSnapshot) {
        }

        public record ProjectConvertInfo(
                        UUID id,
                        String code,
                        String name,
                        String description,
                        UUID programId,
                        UUID operationalPlanId,
                        UUID domainId,
                        String businessArea,
                        UUID projectTypeId,
                        String responsibleDirection,
                        String responsibleTeam,
                        UUID managerId,
                        String projectManager,
                        UUID projectPhaseId,
                        UUID mainSupplierId,
                        String impactedSystem,
                        String expectedBenefits,
                        LocalDate plannedStartDate,
                        LocalDate startDate,
                        LocalDate plannedEndDate,
                        LocalDate endDate,
                        Priority priority,
                        Integer ranking,
                        String budgetLine,
                        BigDecimal budget,
                        PlanType planType,
                        String delayReasons) {
        }

        @ValidReprioritizeRequest
        public record ReprioritizePortfolioRankRequest(
                        @NotNull(message = "newPosition é obrigatório") @PositiveOrZero Integer newPosition,
                        @NotBlank(message = "reprioritizationReason é obrigatório") @Size(max = 80) String reprioritizationReason,
                       // @Size(min = 10, max = 10000, message = "reprioritizationJustification deve ter entre 10 e 10000 caracteres")
                        String reprioritizationJustification) {
        }

        public record ReprioritizePortfolioRankResponse(
                        UUID id,
                        String code,
                        String title,
                        Integer previousPortfolioRank,
                        Integer newPortfolioRank,
                        String status,
                        String reprioritizationReason,
                        String reprioritizationJustification,
                        Instant reprioritizedAt,
                        String reprioritizedBy,
                        List<RankedDemandInfo> affectedDemands) {
        }

        public record RankedDemandInfo(
                        UUID id,
                        String code,
                        String title,
                        Integer previousPortfolioRank,
                        Integer newPortfolioRank) {
        }

        public record ParticipatingDirectionCreate(
                        @NotBlank @Size(max = 120) String directionName,
                        @NotBlank @Size(max = 60) String directionCode,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @NotNull DirectionParticipationType participationType,
                        @Size(max = 10000) String observations) {
        }

        public record ParticipatingDirectionUpdate(
                        @NotBlank @Size(max = 120) String directionName,
                        @NotBlank @Size(max = 60) String directionCode,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @NotNull DirectionParticipationType participationType,
                        @Size(max = 10000) String observations) {
        }

        public record ParticipatingDirectionResponse(
                        UUID id,
                        String directionName,
                        String directionCode,
                        String areaName,
                        String areaCode,
                        DirectionParticipationType participationType,
                        String observations,
                        Instant createdAt,
                        String createdBy,
                        Instant updatedAt,
                        String updatedBy,
                        Long version) {
        }

        public record DemandProfileRequirementInput(
                        @NotNull UUID profileId,
                        @NotNull @Positive Integer requiredQuantity,
                        @NotNull Integer allocationPercentage) {
        }

        public record DemandProfileRequirementResponse(
                        UUID id,
                        UUID profileId,
                        String profileName,
                        ProfileCategory profileCategory,
                        Integer availableCapacity,
                        boolean profileActive,
                        Integer requiredQuantity,
                        Integer allocationPercentage) {
        }
}
