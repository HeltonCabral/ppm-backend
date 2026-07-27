package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import cvt.cv.ppmbackend.enums.OperationalPlanStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operational_plans", uniqueConstraints = @UniqueConstraint(name = "uk_op_plan_year", columnNames = {
        "strategic_plan_id", "fiscal_year" }))
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class OperationalPlan extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategic_plan_id", nullable = false)
    @JsonIgnore
    private StrategicPlan strategicPlan;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperationalPlanStatus status = OperationalPlanStatus.DRAFT;
    @Column(nullable = false)
    private Integer version = 1;
    @Column(name = "approved_budget", precision = 18, scale = 2)
    private BigDecimal approvedBudget;
    @Column(name = "total_budget", precision = 18, scale = 2)
    private BigDecimal totalBudget;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "closed_at")
    private Instant closedAt;

    @JsonProperty("strategicPlanId")
    public UUID getStrategicPlanId() {
        return strategicPlan != null ? strategicPlan.getId() : null;
    }
}
