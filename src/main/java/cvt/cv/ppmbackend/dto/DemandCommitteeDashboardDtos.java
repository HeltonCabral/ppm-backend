package cvt.cv.ppmbackend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class DemandCommitteeDashboardDtos {
    private DemandCommitteeDashboardDtos() {
    }

    public record CommitteeDashboardResponse(
            StrategicPlanHeader strategicPlan,
            ScenarioSummary scenario,
            List<PortfolioAttentionPoint> attentionPoints) {
    }

    public record StrategicPlanHeader(
            UUID id,
            String name,
            Integer startYear,
            Integer endYear,
            String status,
            String statusLabel,
            int deliberatedCount,
            int totalCount,
            int deliberatedPercentage) {
    }

    public record ScenarioSummary(
            int demandsInScenario,
            BigDecimal requiredBudget,
            BigDecimal expectedCapacityPercentage,
            int pendingDecisions,
            int openConditions) {
    }

    public record PortfolioAttentionPoint(
            String code,
            String severity,
            String title,
            int count,
            String detail) {
    }
}
