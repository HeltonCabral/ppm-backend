package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectArea;
import cvt.cv.ppmbackend.entity.ProjectSuccessCriterion;
import cvt.cv.ppmbackend.entity.Supplier;
import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.Priority;
import cvt.cv.ppmbackend.enums.ProjectProfileOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link #from} é o resumo (listas e programas, sem tocar em coleções lazy); {@link #detailed} acrescenta
 * as áreas, os critérios de sucesso e os dados herdados da Demanda, e só deve ser usado dentro de uma
 * transação.
 */
public record ProjectResponse(UUID id, String code, String name, String description, String origin,
        String sourceDemandCode, Boolean createdFromConditionalPlanApproval, Boolean createdFromDemandBacklog,
        UUID programId, UUID sourceDemandId, UUID strategicPlanId, UUID operationalPlanId,
        UUID strategicPillarId, UUID strategicObjectiveId, UUID supplierId,
        String programName, String directionCode, String directionName, String areaCode,
        String areaName, String domain, String supplierName, BigDecimal budget, Integer executionRank, String status,
        String projectPhase, String projectManager, String extraPlanJustification, ProjectExecutionResponse execution,
        List<AreaResponse> areas, List<String> successCriteria, DemandData demandData,
        List<ProfileSummary> profiles, String currentPhaseName, java.time.Instant lastReportAt,
        cvt.cv.ppmbackend.enums.ExecutiveStatus lastReportStatus, Boolean needsSupport, BigDecimal baselineBudget,
        LocalDate baselineEndDate, String projectManagerUsername) {

    /** Perfil previsto, quantas pessoas da equipa já lhe estão associadas e de onde veio. */
    public record ProfileSummary(UUID id, UUID profileId, String profileName, String directionCode,
            Integer requiredQuantity, long assignedCount, ProjectProfileOrigin origin) {
    }

    public record AreaResponse(UUID id, String directionCode, String directionName, String areaCode,
            String areaName, AreaRole role, DirectionParticipationType participationType) {
    }

    /** Dados que o projeto herdou da Demanda de origem. */
    public record DemandData(UUID phaseModelId, String phaseModelName, DemandNature nature,
            DemandComplexity complexity, Priority priority, String sponsor, String requester, String easyVistaRef,
            String problemStatement, String currentEvidence, String proposedResponse, String expectedBenefits,
            String risksIdentified, String dependenciesIdentified, String impactedSystem, String businessArea,
            String budgetLine, BigDecimal estimatedBudget, LocalDate mandatoryDeadline, List<String> suppliers,
            Map<String, Object> typeFields) {
    }

    public static ProjectResponse from(Project p) {
        return build(p, null, null, null, null);
    }

    public static ProjectResponse detailed(Project p) {
        List<AreaResponse> areas = p.getAreas().stream()
                .sorted(Comparator.comparing((ProjectArea a) -> a.getRole() == AreaRole.RESPONSIBLE ? 0 : 1)
                        .thenComparing(ProjectArea::getDirectionCode, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProjectArea::getAreaCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> new AreaResponse(a.getId(), a.getDirectionCode(), a.getDirectionName(), a.getAreaCode(),
                        a.getAreaName(), a.getRole(), a.getParticipationType()))
                .toList();
        List<String> criteria = p.getSuccessCriteria().stream().map(ProjectSuccessCriterion::getDescription).toList();
        DemandData demandData = new DemandData(
                p.getPhaseModel() != null ? p.getPhaseModel().getId() : null,
                p.getPhaseModel() != null ? p.getPhaseModel().getName() : null,
                p.getDemandNature(), p.getComplexity(), p.getPriority(), p.getSponsor(), p.getRequester(),
                p.getEasyVistaRef(), p.getProblemStatement(), p.getCurrentEvidence(), p.getProposedResponse(),
                p.getExpectedBenefits(), p.getRisksIdentified(), p.getDependenciesIdentified(), p.getImpactedSystem(),
                p.getBusinessArea(), p.getBudgetLine(), p.getEstimatedBudget(), p.getMandatoryDeadline(),
                p.getSuppliers().stream().map(Supplier::getName).sorted(Comparator.nullsLast(String::compareTo))
                        .toList(),
                p.getTypeFields() == null ? Map.of() : p.getTypeFields());
        List<ProfileSummary> profiles = p.getProfiles().stream()
                .map(pp -> new ProfileSummary(pp.getId(), pp.getProfile().getId(), pp.getProfileName(),
                        pp.getDirectionCode(), pp.getRequiredQuantity(),
                        p.getTeamMembers().stream().filter(m -> m.getProjectProfile() != null
                                && pp.getId().equals(m.getProjectProfile().getId())).count(),
                        pp.getOrigin() == null ? ProjectProfileOrigin.DEMAND : pp.getOrigin()))
                .toList();
        return build(p, areas, criteria, demandData, profiles);
    }

    private static ProjectResponse build(Project p, List<AreaResponse> areas, List<String> criteria,
            DemandData demandData, List<ProfileSummary> profiles) {
        return new ProjectResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), text(p.getOrigin()),
                p.getSourceDemand() != null ? p.getSourceDemand().getCode() : null,
                p.getCreatedFromConditionalPlanApproval(), p.getCreatedFromDemandBacklog(),
                p.getProgram() != null ? p.getProgram().getId() : null,
                p.getSourceDemand() != null ? p.getSourceDemand().getId() : null,
                p.getStrategicPlan() != null ? p.getStrategicPlan().getId() : null,
                p.getOperationalPlan() != null ? p.getOperationalPlan().getId() : null,
                p.getStrategicPillar() != null ? p.getStrategicPillar().getId() : null,
                p.getStrategicObjective() != null ? p.getStrategicObjective().getId() : null,
                p.getMainSupplier() != null ? p.getMainSupplier().getId() : null,
                p.getProgram() != null ? p.getProgram().getName() : null, p.getDirectionCode(), p.getDirectionName(),
                p.getAreaCode(), p.getAreaName(), p.getDomain() != null ? p.getDomain().getLabel() : null,
                p.getMainSupplier() != null ? p.getMainSupplier().getName() : null, p.getBudget(), p.getExecutionRank(),
                text(p.getStatus()), p.getProjectPhase() != null ? p.getProjectPhase().getLabel() : null,
                p.getProjectManager(), p.getExtraPlanJustification(), ProjectExecutionResponse.from(p.getExecution()),
                areas, criteria, demandData, profiles, p.getCurrentPhaseName(), p.getLastReportAt(),
                p.getLastReportStatus(), p.getLastReportNeedsSupport(), p.getBaselineBudget(), p.getBaselineEndDate(),
                p.getProjectManagerUsername());
    }

    private static String text(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
