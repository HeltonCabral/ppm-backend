package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ProjectPhaseGateStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "project_phase_gate")
@Getter
@Setter
@NoArgsConstructor
public class ProjectPhaseGate extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id", nullable = false, unique = true)
    @JsonIgnore
    private ProjectPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ProjectPhaseGateStatus status = ProjectPhaseGateStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String justification;
    @Column(name = "requested_by", length = 150)
    private String requestedBy;
    @Column(name = "requested_at")
    private Instant requestedAt;
    @Column(name = "decided_by", length = 150)
    private String decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @OneToMany(mappedBy = "gate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ProjectPhaseGateItem> checklist = new ArrayList<>();
}
