package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "demand_planning_cycles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_demand_strategic_plan", columnNames = { "demand_id", "strategic_plan_id" })
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class DemandPlanningCycle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategic_plan_id", nullable = false)
    @JsonIgnore
    private StrategicPlan strategicPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operational_plan_id", nullable = false)
    @JsonIgnore
    private OperationalPlan operationalPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_pillar_id")
    @JsonIgnore
    private StrategicPillar strategicPillar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_element_id")
    @JsonIgnore
    private StrategicElement strategicElement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_objective_id")
    @JsonIgnore
    private StrategicObjective strategicObjective;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    @JsonIgnore
    private Program program;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DemandPlanningStatus status = DemandPlanningStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_committee_id")
    @JsonIgnore
    private Committee responsibleCommittee;

    @Column(name = "in_strategic_committee", nullable = false)
    private boolean inStrategicCommittee;

    @Column(name = "strategic_committee_at")
    private Instant strategicCommitteeAt;

    @Column(name = "score_total", precision = 19, scale = 2)
    private BigDecimal scoreTotal;

    @Column(name = "score_status", nullable = false, length = 30)
    private String scoreStatus = "Não Calculado";

    @Column(name = "score_calculated_at")
    private Instant scoreCalculatedAt;

    @Column(name = "score_invalidated_at")
    private Instant scoreInvalidatedAt;

    @Column(name = "score_invalidation_reason", columnDefinition = "TEXT")
    private String scoreInvalidationReason;

    @Column(name = "previous_score_snapshot", columnDefinition = "TEXT")
    private String previousScoreSnapshot;

    @Column(name = "portfolio_rank")
    private Integer portfolioRank;

    @Column(name = "direction_rank")
    private Integer directionRank;

    @Column(name = "committee_rank")
    private Integer committeeRank;

    @Column(name = "capacity_status", length = 40)
    private String capacityStatus;

    @Column(name = "risk_status", length = 40)
    private String riskStatus;

    @Column(name = "approval_type", length = 40)
    private String approvalType;

    @Column(name = "committee_decision", length = 60)
    private String committeeDecision;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @Column(name = "decision_by", length = 150)
    private String decisionBy;

    @Column(name = "reprioritization_reason", length = 80)
    private String reprioritizationReason;

    @Column(name = "reprioritization_justification", columnDefinition = "TEXT")
    private String reprioritizationJustification;

    @Column(name = "reprioritized_at")
    private Instant reprioritizedAt;

    @Column(name = "reprioritized_by", length = 150)
    private String reprioritizedBy;

    @Column(name = "carried_over", nullable = false)
    private boolean carriedOver = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_cycle_id")
    @JsonIgnore
    private DemandPlanningCycle previousCycle;

    @Column(name = "cycle_entry_at", nullable = false)
    private Instant cycleEntryAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_project_id")
    @JsonIgnore
    private Project convertedProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_program_id")
    @JsonIgnore
    private Program convertedProgram;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "converted_by", length = 150)
    private String convertedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
