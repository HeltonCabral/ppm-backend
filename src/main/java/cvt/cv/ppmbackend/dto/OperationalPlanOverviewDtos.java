package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.OperationalPlanStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OperationalPlanOverviewDtos {

    record OperationalPlanRef(
            UUID id,
            String name,
            Integer fiscalYear,
            OperationalPlanStatus status,
            UUID strategicPlanId,
            String strategicPlanName,
            BigDecimal approvedBudget,
            BigDecimal totalBudget) {
    }

    record Metrics(
            long demandsInScenario,
            long approvedDemands,
            long conditionallyApprovedDemands,
            long backlogDemands,
            long pendingDemands,
            int deliberationPercentage,
            long projectsGenerated) {
    }

    record NextOperationalCycle(
            Integer fiscalYear,
            boolean canStart) {
    }

    record OperationalPlanHistoryItem(
            UUID id,
            String name,
            Integer fiscalYear,
            OperationalPlanStatus status,
            UUID strategicPlanId,
            String strategicPlanName,
            long projectsCount) {
    }

    record Overview(
            OperationalPlanRef activePlan,
            OperationalPlanRef planningPlan,
            Metrics metrics,
            NextOperationalCycle nextOperationalCycle,
            List<OperationalPlanHistoryItem> history) {
    }
}
