package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.ReviewFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "strategic_planning_configs")
@Getter
@Setter
@NoArgsConstructor
public class StrategicPlanningConfig extends BaseEntity {
    @Column(name = "rolling_plan", nullable = false)
    private boolean rollingPlan = true;

    @Column(name = "horizon_years", nullable = false)
    private Integer horizonYears = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_frequency", nullable = false, length = 20)
    private ReviewFrequency reviewFrequency = ReviewFrequency.ANNUAL;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
