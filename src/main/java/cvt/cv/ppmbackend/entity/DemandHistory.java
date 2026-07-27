package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "demand_history", indexes = {
        @Index(name = "idx_demand_history_demand", columnList = "demand_id"),
        @Index(name = "idx_demand_history_occurred", columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class DemandHistory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;
    @Column(name = "previous_status", length = 50)
    private String previousStatus;
    @Column(name = "new_status", length = 50)
    private String newStatus;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "actor_id", length = 150)
    private String actorId;
    @Column(name = "actor_name", length = 150)
    private String actorName;
    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(columnDefinition = "TEXT")
    private String metadata;
}
