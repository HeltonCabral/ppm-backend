package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.dto.CapacityDtos.PeriodUtilization;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandPhaseRef;
import cvt.cv.ppmbackend.enums.CapacityStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public class DemandCapacityAnalysisDtos {
    private DemandCapacityAnalysisDtos() {
    }

    public record CapacityAnalysisResponse(
            CapacityStatus status,
            BigDecimal peakUtilization,
            YearMonth criticalPeriod,
            String mainConflict,
            String reason,
            List<ProfileCapacityAnalysis> profiles) {
    }

    public record ProfileCapacityAnalysis(
            UUID requirementId,
            UUID profileId,
            String profileName,
            String directionCode,
            boolean active,
            BigDecimal requiredFte,
            Integer availableCapacity,
            Integer simultaneousDemandCapacity,
            CapacityStatus status,
            BigDecimal peakUtilization,
            YearMonth criticalPeriod,
            BigDecimal requiredLoadAtPeak,
            int simultaneousDemandsAtPeak,
            List<PeriodUtilization> periods,
            List<CompetingDemand> competingDemands,
            List<DemandPhaseRef> phases) {
    }

    public record CompetingDemand(
            UUID demandId,
            String code,
            String title,
            BigDecimal requiredFte,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }
}
