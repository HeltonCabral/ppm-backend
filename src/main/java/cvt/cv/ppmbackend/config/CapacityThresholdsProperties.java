package cvt.cv.ppmbackend.config;

import cvt.cv.ppmbackend.enums.CapacityStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Limites de utilização (em %) que separam os estados de capacidade. Valores exclusivos no limite
 * inferior e inclusivos no superior: OK ≤ ok < CONDITIONED ≤ conditioned < RESTRICTED ≤ restricted
 * < CRITICAL.
 */
@Component
@ConfigurationProperties(prefix = "ppm.capacity.thresholds")
public class CapacityThresholdsProperties {

    private BigDecimal ok = new BigDecimal("80");
    private BigDecimal conditioned = new BigDecimal("100");
    private BigDecimal restricted = new BigDecimal("120");

    public BigDecimal getOk() { return ok; }
    public void setOk(BigDecimal ok) { this.ok = ok; }

    public BigDecimal getConditioned() { return conditioned; }
    public void setConditioned(BigDecimal conditioned) { this.conditioned = conditioned; }

    public BigDecimal getRestricted() { return restricted; }
    public void setRestricted(BigDecimal restricted) { this.restricted = restricted; }

    @PostConstruct
    void validate() {
        if (ok.signum() <= 0 || conditioned.compareTo(ok) <= 0 || restricted.compareTo(conditioned) <= 0) {
            throw new IllegalStateException(
                    "ppm.capacity.thresholds inválidos: exige 0 < ok < conditioned < restricted (actual: "
                            + ok + " / " + conditioned + " / " + restricted + ")");
        }
    }

    public CapacityStatus classify(BigDecimal utilizationPercent) {
        if (utilizationPercent == null) {
            return CapacityStatus.NOT_CONFIGURED;
        }
        if (utilizationPercent.compareTo(ok) <= 0) return CapacityStatus.OK;
        if (utilizationPercent.compareTo(conditioned) <= 0) return CapacityStatus.CONDITIONED;
        if (utilizationPercent.compareTo(restricted) <= 0) return CapacityStatus.RESTRICTED;
        return CapacityStatus.CRITICAL;
    }
}
