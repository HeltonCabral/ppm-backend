package cvt.cv.ppmbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "cycle_audit_log", indexes = @Index(name = "idx_cycle_audit", columnList = "cycle_id,created_at"))
@Getter
@Setter
@NoArgsConstructor
public class CycleAuditLog extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id")
    private StrategicPlan cycle;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(name = "from_status", length = 30)
    private String fromStatus;
    @Column(name = "to_status", length = 30)
    private String toStatus;
    @Column(columnDefinition = "TEXT")
    private String comment;
    @Column(name = "performed_by", nullable = false, length = 150)
    private String performedBy;
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
