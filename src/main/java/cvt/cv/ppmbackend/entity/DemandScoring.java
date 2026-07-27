package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "demand_scoring", uniqueConstraints = @UniqueConstraint(name = "uk_demand_scoring_demand_criterion", columnNames = {
        "demand_id", "criterion_id" }), indexes = {
                @Index(name = "idx_demand_scoring_demand", columnList = "demand_id"),
                @Index(name = "idx_demand_scoring_criterion", columnList = "criterion_id"),
                @Index(name = "idx_demand_scoring_scored_at", columnList = "scored_at")
        })
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class DemandScoring extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private ScoringCriterion criterion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal score;

    @Column(name = "weighted_score", nullable = false, precision = 12, scale = 4)
    private BigDecimal weightedScore;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "scored_at", nullable = false, updatable = false)
    private Instant scoredAt;

    @Column(name = "scored_by", length = 150)
    private String scoredBy;
}
