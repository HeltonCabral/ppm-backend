package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.CapacityStatus;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** DTOs partilhados pelo cálculo de capacidade (por perfil e por período). */
public final class CapacityDtos {
    private CapacityDtos() {}

    /** Utilização de um perfil ao longo de todos os períodos em que tem carga. */
    public record ProfileUtilization(
            UUID profileId,
            String profileName,
            String directionCode,
            boolean active,
            Integer availableCapacity,
            Integer simultaneousDemandCapacity,
            CapacityStatus status,
            BigDecimal peakUtilization,
            YearMonth criticalPeriod,
            BigDecimal requiredLoadAtPeak,
            int simultaneousDemandsAtPeak,
            int unscheduledDemands,
            BigDecimal unscheduledLoad,
            List<PeriodUtilization> periods) {
    }

    /** Utilização de um perfil num período (mês). */
    public record PeriodUtilization(
            YearMonth period,
            BigDecimal requiredLoad,
            int simultaneousDemands,
            BigDecimal peopleUtilization,
            BigDecimal simultaneityUtilization,
            BigDecimal utilization,
            CapacityStatus status) {
    }
}
