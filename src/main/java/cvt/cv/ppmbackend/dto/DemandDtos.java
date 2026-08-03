package cvt.cv.ppmbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
                        @Size(max = 120) String area,
                        @Size(max = 120) String direction,
                        @Size(max = 150) String sponsor,
                        @NotNull UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        UUID strategicPlanId,
                        UUID operationalPlanId,
                        UUID strategicPillarId,
                        UUID strategicObjectiveId,
                        UUID programId,
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
                        BigDecimal scoreValue,
                        BigDecimal scoreEffort,
                        BigDecimal scoreRisk,
                        BigDecimal scoreTotal,
                        @PositiveOrZero Integer portfolioRank,
                        @Size(max = 40) String approvalType,
                        @Size(max = 60) String committeeDecision,
                        @Size(max = 10000) String rejectionReason,
                        @Valid List<AttachmentInput> attachments) {
        }

        public record Update(
                        @NotBlank @Size(max = 250) String title,
                        @Size(max = 10000) String description,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String area,
                        @Size(max = 120) String direction,
                        @Size(max = 150) String sponsor,
                        @NotNull UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        UUID strategicPlanId,
                        UUID operationalPlanId,
                        UUID strategicPillarId,
                        UUID strategicObjectiveId,
                        UUID programId,
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
                        BigDecimal scoreValue,
                        BigDecimal scoreEffort,
                        BigDecimal scoreRisk,
                        BigDecimal scoreTotal,
                        @PositiveOrZero Integer portfolioRank,
                        @Size(max = 40) String approvalType,
                        @Size(max = 60) String committeeDecision,
                        @Size(max = 10000) String rejectionReason) {
        }

        public record Patch(
                        @Size(max = 250) String title,
                        @Size(max = 10000) String description,
                        @Size(max = 150) String requester,
                        @Size(max = 120) String area,
                        @Size(max = 120) String direction,
                        @Size(max = 150) String sponsor,
                        UUID typeId,
                        @Size(max = 80) String origin,
                        @Size(max = 120) String easyVistaRef,
                        UUID strategicPlanId,
                        UUID operationalPlanId,
                        UUID strategicPillarId,
                        UUID strategicObjectiveId,
                        UUID programId,
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
                        BigDecimal scoreValue,
                        BigDecimal scoreEffort,
                        BigDecimal scoreRisk,
                        BigDecimal scoreTotal,
                        @PositiveOrZero Integer portfolioRank,
                        @Size(max = 40) String approvalType,
                        @Size(max = 60) String committeeDecision,
                        @Size(max = 10000) String rejectionReason) {
        }

        public record StatusPatch(@NotBlank @Size(max = 50) String status, @Size(max = 4000) String reason) {
        }

        public record AttachmentCreate(
                        @NotBlank @Size(max = 250) String name,
                        @NotBlank @Size(max = 1000) String url,
                        @Size(max = 150) String contentType) {
        }

        public record ConvertToProject(
                        @NotBlank @Size(max = 180) String projectName,
                        UUID programId,
                        UUID managerId,
                        @Size(max = 120) String projectManager,
                        LocalDate startDate,
                        LocalDate endDate) {
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

        public record ProjectSummary(UUID id, String name, String status) {
        }

        public record DemandResponse(
                        UUID id,
                        String code,
                        String title,
                        String description,
                        String requester,
                        String area,
                        String direction,
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
                        String capacityStatus,
                        String riskStatus,
                        String risksIdentified,
                        String dependenciesIdentified,
                        BigDecimal scoreValue,
                        BigDecimal scoreEffort,
                        BigDecimal scoreRisk,
                        BigDecimal scoreTotal,
                        Integer portfolioRank,
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
                        ProjectSummary convertedProject,
                        List<DemandAttachmentResponse> attachments,
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

        public record DemandConvertInfo(UUID id, String code, String status, UUID convertedProjectId) {
        }

        public record ProjectConvertInfo(UUID id, String code, String name) {
        }
}
