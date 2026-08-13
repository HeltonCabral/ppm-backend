package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.Supplier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        UUID programId,
        String programName,
        UUID strategicPlanId,
        String strategicPlanName,
        UUID operationalPlanId,
        String operationalPlanName,
        UUID strategicPillarId,
        String strategicPillarName,
        UUID strategicObjectiveId,
        String strategicObjectiveName,
        UUID domainId,
        String domainCode,
        String domainName,
        String businessArea,
        UUID projectTypeId,
        String projectTypeCode,
        String projectTypeName,
        String directionName,
        String directionCode,
        String areaName,
        String areaCode,
        String responsibleDirection,
        String responsibleTeam,
        String projectManager,
        UUID projectManagerId,
        String status,
        UUID projectPhaseId,
        String projectPhaseCode,
        String projectPhaseName,
        UUID mainSupplierId,
        String mainSupplierName,
        List<SupplierRef> suppliers,
        String impactedSystem,
        String scheduleStatus,
        String costStatus,
        String riskStatus,
        String valueStatus,
        String expectedImpact,
        String expectedBenefit,
        String expectedBenefits,
        LocalDate plannedStartDate,
        LocalDate startDate,
        LocalDate plannedEndDate,
        LocalDate endDate,
        LocalDate desiredDate,
        String priority,
        Integer ranking,
        Integer portfolioRank,
        String budgetLine,
        BigDecimal budget,
        BigDecimal estimatedBudget,
        String planType,
        String delayReasons,
        UUID sourceDemandId,
        Integer sourceDemandPortfolioRank,
        Boolean createdFromConditionalPlanApproval) {

    public record SupplierRef(UUID id, String name) {
        private static SupplierRef from(Supplier supplier) {
            return new SupplierRef(supplier.getId(), supplier.getName());
        }
    }

    public static ProjectResponse from(Project p) {
        LookupValue domain = p.getDomain();
        LookupValue projectType = p.getProjectType();
        LookupValue projectPhase = p.getProjectPhase();
        Supplier mainSupplier = p.getMainSupplier();
        List<SupplierRef> supplierRefs = p.getSuppliers() == null
                ? List.of()
                : p.getSuppliers().stream()
                        .map(SupplierRef::from)
                        .sorted(Comparator.comparing(SupplierRef::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(SupplierRef::id, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getProgramId(),
                p.getProgram() != null ? p.getProgram().getName() : null,
                p.getStrategicPlanId(),
                p.getStrategicPlan() != null ? p.getStrategicPlan().getName() : null,
                p.getOperationalPlanId(),
                p.getOperationalPlan() != null ? p.getOperationalPlan().getName() : null,
                p.getStrategicPillarId(),
                p.getStrategicPillar() != null ? p.getStrategicPillar().getName() : null,
                p.getStrategicObjectiveId(),
                p.getStrategicObjective() != null ? p.getStrategicObjective().getName() : null,
                p.getDomainId(),
                code(domain),
                label(domain),
                p.getBusinessArea(),
                id(projectType),
                code(projectType),
                label(projectType),
                p.getDirectionName(),
                p.getDirectionCode(),
                p.getAreaName(),
                p.getAreaCode(),
                p.getResponsibleDirection(),
                p.getResponsibleTeam(),
                p.getProjectManager(),
                p.getProjectManagerId(),
                text(p.getStatus()),
                id(projectPhase),
                code(projectPhase),
                label(projectPhase),
                mainSupplier != null ? mainSupplier.getId() : null,
                mainSupplier != null ? mainSupplier.getName() : null,
                supplierRefs,
                p.getImpactedSystem(),
                text(p.getScheduleStatus()),
                text(p.getCostStatus()),
                text(p.getRiskStatus()),
                text(p.getValueStatus()),
                p.getExpectedImpact(),
                p.getExpectedBenefit(),
                p.getExpectedBenefits(),
                p.getPlannedStartDate(),
                p.getStartDate(),
                p.getPlannedEndDate(),
                p.getEndDate(),
                p.getDesiredDate(),
                text(p.getPriority()),
                p.getRanking(),
                p.getPortfolioRank(),
                p.getBudgetLine(),
                p.getBudget(),
                p.getEstimatedBudget(),
                text(p.getPlanType()),
                p.getDelayReasons(),
                p.getSourceDemandId(),
                p.getSourceDemandPortfolioRank(),
                p.getCreatedFromConditionalPlanApproval());
    }

    private static UUID id(LookupValue value) {
        return value != null ? value.getId() : null;
    }

    private static String code(LookupValue value) {
        return value != null ? value.getCode() : null;
    }

    private static String label(LookupValue value) {
        return value != null ? value.getLabel() : null;
    }

    private static String text(Enum<?> value) {
        return value != null ? value.name() : null;
    }
}