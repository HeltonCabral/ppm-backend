package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.CycleReviewStatus;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cycle_reviews", indexes = { @Index(name = "idx_review_source", columnList = "source_cycle_id"),
        @Index(name = "idx_review_status", columnList = "status") })
@Getter
@Setter
@NoArgsConstructor
public class CycleReview extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_cycle_id")
    private StrategicPlan sourceCycle;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleReviewStatus status = CycleReviewStatus.DRAFT;
    @Column(name = "draft_json", nullable = false, columnDefinition = "TEXT")
    private String draftJson;
    @Column(name = "created_cycle_id")
    private UUID createdCycleId;
    @Column(name = "idempotency_key", unique = true, length = 150)
    private String idempotencyKey;
    @Version
    private Long version;
    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;
    @Column(name = "updated_by", nullable = false, length = 150)
    private String updatedBy;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
