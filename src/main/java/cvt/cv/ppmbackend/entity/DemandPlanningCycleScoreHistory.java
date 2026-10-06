package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "demand_planning_cycle_score_history", indexes = {
        @Index(name = "idx_dpcsh_planning_cycle_id", columnList = "planning_cycle_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandPlanningCycleScoreHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_cycle_id", nullable = false)
    @JsonIgnore
    private DemandPlanningCycle planningCycle;

    @Column(name = "score_total", precision = 19, scale = 2)
    private BigDecimal scoreTotal;

    @Column(name = "score_value", precision = 19, scale = 4)
    private BigDecimal scoreValue;

    @Column(name = "score_effort", precision = 19, scale = 4)
    private BigDecimal scoreEffort;

    @Column(name = "score_risk", precision = 19, scale = 4)
    private BigDecimal scoreRisk;

    @Column(name = "portfolio_rank")
    private Integer portfolioRank;

    @Column(name = "direction_rank")
    private Integer directionRank;

    @Column(name = "committee_rank")
    private Integer committeeRank;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @Column(name = "invalidated_at", nullable = false)
    private Instant invalidatedAt;

    @Column(name = "invalidation_reason", columnDefinition = "TEXT")
    private String invalidationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
