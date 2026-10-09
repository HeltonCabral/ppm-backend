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

    /**
     * Capacidade agregada de um conjunto de Demandas (cálculo mensal). Fonte única para a análise de
     * plano/cenário e para os painéis do comité.
     *
     * @param totalRequiredLoad   soma, por perfil, da carga no mês de pico desse perfil
     * @param profilesOutOfBounds perfis com estado diferente de OK
     * @param profiles            por gravidade decrescente e, em empate, maior pico primeiro
     */
    public record PortfolioCapacity(
            CapacityStatus overallStatus,
            BigDecimal peakUtilization,
            YearMonth criticalPeriod,
            BigDecimal totalAvailableCapacity,
            BigDecimal totalRequiredLoad,
            int profilesOutOfBounds,
            List<ProfileUtilization> profiles) {

        /** Disponível ÷ necessário no pico, em %; {@code null} sem carga. */
        public BigDecimal coveragePercentage() {
            if (totalRequiredLoad == null || totalRequiredLoad.signum() == 0) {
                return null;
            }
            return totalAvailableCapacity.multiply(BigDecimal.valueOf(100))
                    .divide(totalRequiredLoad, 2, java.math.RoundingMode.HALF_UP);
        }

        public List<ProfileUtilization> outOfBoundsProfiles() {
            return profiles.stream().filter(p -> p.status() != CapacityStatus.OK).toList();
        }
    }
}
