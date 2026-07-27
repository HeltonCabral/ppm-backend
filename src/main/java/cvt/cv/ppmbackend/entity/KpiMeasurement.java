package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.KpiMeasurementStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "kpi_measurements", uniqueConstraints = @UniqueConstraint(name = "uk_kpi_measurement_year", columnNames = {
        "kpi_id", "year" }))
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class KpiMeasurement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    @JsonIgnore
    private Kpi kpi;
    @Column(nullable = false)
    private Integer year;
    @Column(precision = 18, scale = 4)
    private BigDecimal current;
    @Column(precision = 18, scale = 4)
    private BigDecimal goal;

    @Transient
    public KpiMeasurementStatus getStatus() {
        if (current == null || goal == null || goal.signum() <= 0)
            return null;
        BigDecimal ratio = current.divide(goal, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.9")) >= 0)
            return KpiMeasurementStatus.NO_ALVO;
        if (ratio.compareTo(new BigDecimal("0.6")) >= 0)
            return KpiMeasurementStatus.ATENCAO;
        return KpiMeasurementStatus.FORA_DO_ALVO;
    }
}
