package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.CommitteeActionType;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Ação de um Comité sobre uma Demanda num ciclo. No modelo de cenários a única decisão
 * individual que subsiste é o pedido de revisão — a aprovação resulta da seleção de um cenário.
 */
@Entity
@Table(name = "demand_committee_actions", indexes = {
        @Index(name = "idx_demand_committee_actions_cycle", columnList = "planning_cycle_id"),
        @Index(name = "idx_demand_committee_actions_requested_at", columnList = "requested_at")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandCommitteeAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_cycle_id", nullable = false)
    @JsonIgnore
    private DemandPlanningCycle planningCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id")
    @JsonIgnore
    private Committee committee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CommitteeActionType action = CommitteeActionType.REQUEST_REVISION;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String justification;

    @Column(name = "requested_by", nullable = false, length = 150)
    private String requestedBy = "system";

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Status do ciclo imediatamente antes da revisão, para onde regressa quando esta é concluída. */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 40)
    private DemandPlanningStatus previousStatus;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
