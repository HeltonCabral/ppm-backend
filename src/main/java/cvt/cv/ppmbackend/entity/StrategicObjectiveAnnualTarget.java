package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "strategic_objective_annual_targets", uniqueConstraints = @UniqueConstraint(name = "uk_annual_target_year", columnNames = {
        "strategic_objective_id", "year" }))
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class StrategicObjectiveAnnualTarget extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategic_objective_id", nullable = false)
    @JsonIgnore
    private StrategicObjective strategicObjective;
    @Column(nullable = false)
    private Integer year;
    @Column(name = "target_label", nullable = false, length = 200)
    private String targetLabel;
    @Column(name = "target_value", precision = 18, scale = 4)
    private BigDecimal targetValue;
    @Column(precision = 6, scale = 4)
    private BigDecimal weight;
}
