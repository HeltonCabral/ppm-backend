package cvt.cv.ppmbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos;
import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.ProfileCategory;
import cvt.cv.ppmbackend.enums.PlanType;
import cvt.cv.ppmbackend.enums.Priority;
import cvt.cv.ppmbackend.enums.RateUnit;
import cvt.cv.ppmbackend.validation.ValidReprioritizeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DemandDtos {
        private DemandDtos() {
        }

        public record AttachmentInput(
                        @NotBlank @Size(max = 250) String name,
                        @NotBlank @Size(max = 1000) String url,
                        @Size(max = 150) String contentType) {
        }

        public record DemandSuccessCriterionInput(
                        @NotBlank @Size(max = 1000) String description) {
        }

        public record DemandSuccessCriterionResponse(
                        UUID id,
                        String description,
                        Integer displayOrder) {
        }

        public record DirectionAreaInput(
                        @NotBlank @Size(max = 60) String areaCode,
                        @Size(max = 120) String areaName,
                        @NotNull AreaRole role) {
        }

        public record DirectionAreaResponse(
                        UUID id,
                        String areaCode,
                        String areaName,
                        AreaRole role) {
        }

        public record Create(
                        @NotBlank @Size(max = 250) String title,
                        UUID strategicPillarId,
                        UUID strategicElementId,
                        UUID strategicObjectiveId,
                        @Size(max = 1000) String summary,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @Size(max = 120) String directionName,
                        @Size(max = 60) String directionCode,
                        DirectionParticipationType directionParticipationType,
                        DemandNature nature,
                        @Size(max = 150) String sponsor,
                        UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        Set<UUID> supplierIds,
                        UUID domainId,
                        UUID budgetLineId,
                        @Size(max = 150) String impactedSystem,
                        @Size(max = 10000) String currentEvidence,
                        @NotBlank @Size(max = 10000) String problemStatement,
                        @NotBlank @Size(max = 10000) String desiredOutcome,
                        @Size(max = 10000) String proposedResponse,
                        @Size(max = 40) String urgency,
                        @PositiveOrZero BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        LocalDate mandatoryDeadline,
                        @Size(max = 10000) String notes,
                        @Size(max = 10000) String risksIdentified,
                        @Size(max = 10000) String dependenciesIdentified,
                        @PositiveOrZero Integer dependenciesCount,
                        @Valid List<AttachmentInput> attachments,
                        @Valid List<ParticipatingDirectionCreate> participatingDirections,
                        @Valid List<DemandProfileRequirementInput> profileRequirements,
                        @Valid List<DemandSuccessCriterionInput> successCriteria,
                        @Valid List<DirectionAreaInput> areas) {
        }

        /**
         * Opções de cópia de uma Demanda. Todos os campos são opcionais: quando
         * omitidos, o título recebe o prefixo "Cópia de" e todas as coleções da
         * demanda de origem são copiadas.
         */
        public record Duplicate(
                        @Size(max = 250) String title,
                        Boolean copyAttachments,
                        Boolean copyParticipatingDirections,
                        Boolean copyProfileRequirements,
                        Boolean copySuccessCriteria,
                        Boolean copyStrategicFraming) {
        }

        public record Update(
                        @NotBlank @Size(max = 250) String title,
                        UUID strategicPillarId,
                        UUID strategicElementId,
                        UUID strategicObjectiveId,
                        @Size(max = 1000) String summary,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @Size(max = 120) String directionName,
                        @Size(max = 60) String directionCode,
                        DirectionParticipationType directionParticipationType,
                        DemandNature nature,
                        @Size(max = 150) String sponsor,
                        UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        Set<UUID> supplierIds,
                        UUID domainId,
                        UUID budgetLineId,
                        @Size(max = 150) String impactedSystem,
                        @Size(max = 10000) String currentEvidence,
                        @NotBlank @Size(max = 10000) String problemStatement,
                        @NotBlank @Size(max = 10000) String desiredOutcome,
                        @Size(max = 10000) String proposedResponse,
                        @Size(max = 40) String urgency,
                        @PositiveOrZero BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        LocalDate mandatoryDeadline,
                        @Size(max = 10000) String notes,
                        @Size(max = 10000) String risksIdentified,
                        @Size(max = 10000) String dependenciesIdentified,
                        @PositiveOrZero Integer dependenciesCount,
                        @Valid List<ParticipatingDirectionCreate> participatingDirections,
                        @Valid List<DemandProfileRequirementInput> profileRequirements,
                        @Valid List<DemandSuccessCriterionInput> successCriteria,
                        @Valid List<DirectionAreaInput> areas) {
        }

        public record Patch(
                        @Size(max = 250) String title,
                        UUID strategicPillarId,
                        UUID strategicElementId,
                        UUID strategicObjectiveId,
                        @Size(max = 1000) String summary,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @Size(max = 120) String directionName,
                        @Size(max = 60) String directionCode,
                        DirectionParticipationType directionParticipationType,
                        DemandNature nature,
                        @Size(max = 150) String sponsor,
                        UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        Set<UUID> supplierIds,
                        UUID domainId,
                        UUID budgetLineId,
                        @Size(max = 150) String impactedSystem,
                        @Size(max = 10000) String currentEvidence,
                        @Size(max = 10000) String problemStatement,
                        @Size(max = 10000) String desiredOutcome,
                        @Size(max = 10000) String proposedResponse,
                        @Size(max = 40) String urgency,
                        @PositiveOrZero BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        LocalDate mandatoryDeadline,
                        @Size(max = 10000) String notes,
                        @Size(max = 10000) String risksIdentified,
                        @Size(max = 10000) String dependenciesIdentified,
                        @PositiveOrZero Integer dependenciesCount,
                        @Valid List<ParticipatingDirectionCreate> participatingDirections,
                        @Valid List<DemandProfileRequirementInput> profileRequirements,
                        @Valid List<DemandSuccessCriterionInput> successCriteria,
                        @Valid List<DirectionAreaInput> areas) {
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

        public record PhaseModelSummary(UUID id, String code, String name, boolean active) {
        }

        public record StrategicPlanSummary(UUID id, String name, Integer startYear, Integer endYear, String status) {
        }

        public record OperationalPlanSummary(UUID id, String name, Integer fiscalYear, String status) {
        }

        public record StrategicPillarSummary(UUID id, String name, String description) {
        }

        public record StrategicElementSummary(UUID id, String name, String description) {
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

        public record CurrencySummary(UUID id, String code, String name, String symbol) {
        }

        public record SupplierSummary(
                        UUID id,
                        String name,
                        String country,
                        BigDecimal ratePerHour,
                        RateUnit rateUnit,
                        CurrencySummary currency) {
        }

        public record DemandResponse(
                        UUID id,
                        String code,
                        String title,
                        StrategicPillarSummary strategicPillar,
                        StrategicElementSummary strategicElement,
                        StrategicObjectiveSummary strategicObjective,
                        String summary,
                        String requester,
                        String areaName,
                        String areaCode,
                        String directionName,
                        String directionCode,
                        DirectionParticipationType directionParticipationType,
                        DemandNature nature,
                        String sponsor,
                        UUID typeId,
                        String type,
                        String origin,
                        String easyVistaRef,
                        UUID domainId,
                        String domain,
                        UUID budgetLineId,
                        String budgetLine,
                        String impactedSystem,
                        String currentEvidence,
                        String problemStatement,
                        String desiredOutcome,
                        String proposedResponse,
                        String urgency,
                        BigDecimal estimatedBudget,
                        LocalDate desiredDate,
                        LocalDate mandatoryDeadline,
                        String notes,
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
                        Instant createdAt,
                        String createdBy,
                        Instant updatedAt,
                        String updatedBy,
                        Long version,
                        PhaseModelSummary typeData,
                        LookupValueSummary domainData,
                        LookupValueSummary budgetLineData,
                        DemandPlanningCycleDtos.Response currentPlanningCycle,
                        List<DemandAttachmentResponse> attachments,
                        List<ParticipatingDirectionResponse> participatingDirections,
                        List<DemandProfileRequirementResponse> profileRequirements,
                        List<SupplierSummary> suppliers,
                        List<DemandSuccessCriterionResponse> successCriteria,
                        List<DemandDependencyDtos.Response> dependencies,
                        List<DirectionAreaResponse> areas) {
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
                        DemandPlanningStatus status,
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
                        DemandPlanningStatus status,
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
                        @Size(max = 10000) String observations,
                        @Valid List<DirectionAreaInput> areas) {
        }

        public record ParticipatingDirectionUpdate(
                        @NotBlank @Size(max = 120) String directionName,
                        @NotBlank @Size(max = 60) String directionCode,
                        @Size(max = 120) String areaName,
                        @Size(max = 60) String areaCode,
                        @NotNull DirectionParticipationType participationType,
                        @Size(max = 10000) String observations,
                        @Valid List<DirectionAreaInput> areas) {
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
                        Long version,
                        List<DirectionAreaResponse> areas) {
        }

        public record DemandProfileRequirementInput(
                        UUID id,
                        @NotNull UUID profileId,
                        @NotNull @jakarta.validation.constraints.Min(value = 1,
                                message = "requiredQuantity deve ser maior ou igual a 1.")
                        Integer requiredQuantity,
                        @NotEmpty(message = "A lista de fases é obrigatória.")
                        List<@NotNull(message = "A lista de fases não pode conter valores nulos.") UUID> phaseIds) {
        }

        public record DemandProfileRequirementResponse(
                        UUID id,
                        UUID profileId,
                        String profileName,
                        String directionCode,
                        Integer availableCapacity,
                        boolean profileActive,
                        Integer requiredQuantity,
                        List<DemandPhaseRef> phases) {
        }

        public record DemandPhaseRef(
                        UUID phaseId,
                        String phaseCode,
                        String phaseName,
                        Integer phaseSequence) {
        }
}
