package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "operational_plan_baseline")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class OperationalPlanBaseline extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operational_plan_id", nullable = false)
    @JsonIgnore
    private OperationalPlan operationalPlan;
    @Column(nullable = false)
    private Integer version;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(precision = 18, scale = 2)
    private BigDecimal budget;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(name = "planned_progress")
    private Double plannedProgress;
    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @JsonProperty("operationalPlanId")
    public UUID getOperationalPlanId() {
        return operationalPlan != null ? operationalPlan.getId() : null;
    }
}
