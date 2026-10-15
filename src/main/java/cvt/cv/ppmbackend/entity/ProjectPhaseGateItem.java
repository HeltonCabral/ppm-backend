package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project_phase_gate_item")
@Getter
@Setter
@NoArgsConstructor
public class ProjectPhaseGateItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gate_id", nullable = false)
    @JsonIgnore
    private ProjectPhaseGate gate;
    @Column(nullable = false, length = 300)
    private String label;
    @Column(nullable = false)
    private boolean met;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
