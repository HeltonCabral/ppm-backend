package cvt.cv.ppmbackend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class DemandCapacityAnalysisDtos {
    private DemandCapacityAnalysisDtos() {
    }

    public record CapacityAnalysisResponse(
            String status,
            String mainConflict,
            List<ProfileCapacityAnalysis> profiles) {
    }

    public record ProfileCapacityAnalysis(
            UUID profileId,
            String profileName,
            String directionCode,
            boolean active,
            Integer requiredQuantity,
            Integer allocationPercentage,
            Integer availableCapacity,
            Integer simultaneousLimit,
            int simultaneousUsed,
            String situation,
            List<CompetingDemand> competingDemands) {
    }

    public record CompetingDemand(
            UUID demandId,
            String code,
            String title,
            Integer requiredQuantity,
            Integer allocationPercentage,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }
}
