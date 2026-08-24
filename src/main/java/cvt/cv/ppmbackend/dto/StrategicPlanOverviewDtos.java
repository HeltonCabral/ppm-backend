package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ReviewFrequency;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;

import java.time.LocalDate;
import java.util.UUID;

public interface StrategicPlanOverviewDtos {

    record PlanningProgress(
            long totalDemands,
            long deliberatedDemands,
            long approvedDemands,
            long conditionallyApprovedDemands,
            long backlogDemands,
            long pendingDemands,
            int deliberationPercentage,
            long projectsGenerated) {
    }

    record PlanSummary(
            UUID id,
            String name,
            Integer startYear,
            Integer endYear,
            Integer horizonYears,
            Integer revisionNumber,
            StrategicPlanStatus status,
            LocalDate approvalDate,
            String approvedBy) {
    }

    record PlanningPlanSummary(
            UUID id,
            String name,
            Integer startYear,
            Integer endYear,
            Integer horizonYears,
            Integer revisionNumber,
            StrategicPlanStatus status,
            PlanningProgress progress) {
    }

    record ConfigurationSummary(
            boolean rollingPlan,
            Integer horizonYears,
            ReviewFrequency reviewFrequency) {
    }

    record NextPeriod(
            Integer startYear,
            Integer endYear,
            boolean canStart) {
    }

    record Metrics(
            long demandsInScenario,
            long projectsGenerated,
            long operationalPlans) {
    }

    record Overview(
            PlanSummary activePlan,
            PlanningPlanSummary planningPlan,
            ConfigurationSummary configuration,
            NextPeriod nextPeriod,
            Metrics metrics) {
    }

    record PlanListItem(
            UUID id,
            String name,
            Integer startYear,
            Integer endYear,
            Integer horizonYears,
            Integer revisionNumber,
            StrategicPlanStatus status,
            LocalDate approvalDate,
            String approvedBy,
            long projectsCount) {
    }
}
