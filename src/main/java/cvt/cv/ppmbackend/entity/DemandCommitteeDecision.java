package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.CommitteeDecision;
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
 * Histórico imutável das deliberações do Comité sobre uma Demanda num ciclo de planeamento.
 * Cada decisão gera um novo registo — registos anteriores nunca são alterados nem removidos.
 */
@Entity
@Table(name = "demand_committee_decisions", indexes = {
        @Index(name = "idx_demand_committee_decisions_cycle", columnList = "planning_cycle_id"),
        @Index(name = "idx_demand_committee_decisions_decision_at", columnList = "decision_at")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandCommitteeDecision extends BaseEntity {

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
    private CommitteeDecision decision;

    @Column(columnDefinition = "TEXT")
    private String justification;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "decision_at", nullable = false)
    private Instant decisionAt;

    @Column(name = "decided_by", nullable = false, length = 150)
    private String decidedBy = "system";

    @Column(name = "meeting_reference", length = 200)
    private String meetingReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
