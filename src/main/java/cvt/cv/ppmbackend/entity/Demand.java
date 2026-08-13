package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "demands", indexes = {
        @Index(name = "idx_demands_code", columnList = "code", unique = true),
        @Index(name = "idx_demands_status", columnList = "status"),
        @Index(name = "idx_demands_origin", columnList = "origin"),
        @Index(name = "idx_demands_created_at", columnList = "created_at"),
        @Index(name = "idx_demands_responsible_committee_id", columnList = "responsible_committee_id")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Demand extends BaseEntity {
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 150)
    private String requester;

    @Column(length = 120)
    private String areaName;

    @Column(length = 60)
    private String areaCode;

    @Column(length = 120)
    private String directionName;

    @Column(length = 60)
    private String directionCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private DirectionParticipationType directionParticipationType;


    @Column(length = 150)
    private String sponsor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = false)
    private LookupValue type;

    @Column(nullable = false, length = 80)
    private String origin = "MANUAL";

    @Column(name = "easy_vista_ref", length = 120)
    private String easyVistaRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_plan_id")
    @JsonIgnore
    private StrategicPlan strategicPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_plan_id")
    @JsonIgnore
    private OperationalPlan operationalPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_pillar_id")
    @JsonIgnore
    private StrategicPillar strategicPillar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_objective_id")
    @JsonIgnore
    private StrategicObjective strategicObjective;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    @JsonIgnore
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_committee_id")
    @JsonIgnore
    private Committee responsibleCommittee;

    @Column(name = "pre_score", precision = 5, scale = 2)
    private BigDecimal preScore;

    @Column(name = "pre_score_classification", length = 20)
    private String preScoreClassification;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "domain_id")
    private LookupValue domain;

    @Column(name = "impacted_system", length = 150)
    private String impactedSystem;

    @Column(name = "initial_priority", length = 40)
    private String initialPriority;

    @Column(name = "estimated_effort", length = 40)
    private String estimatedEffort;

    @Column(name = "expected_impact", columnDefinition = "TEXT")
    private String expectedImpact;

    @Column(name = "expected_benefit", columnDefinition = "TEXT")
    private String expectedBenefit;

    @Column(length = 40)
    private String urgency;

    @Column(name = "estimated_budget", precision = 19, scale = 2)
    private BigDecimal estimatedBudget;

    @Column(name = "desired_date")
    private LocalDate desiredDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 50)
    private String status = "IN_ANALYSIS";

    @Column(name = "in_strategic_committee", nullable = false)
    @ColumnDefault("false")
    private boolean inStrategicCommittee;

    @Column(name = "strategic_committee_at")
    private Instant strategicCommitteeAt;

    @Column(name = "capacity_status", length = 40)
    private String capacityStatus;

    @Column(name = "risk_status", length = 40)
    private String riskStatus;

    @Column(name = "risks_identified", columnDefinition = "TEXT")
    private String risksIdentified;

    @Column(name = "dependencies_identified", columnDefinition = "TEXT")
    private String dependenciesIdentified;

    @Column(name = "score_total", precision = 19, scale = 2)
    private BigDecimal scoreTotal;

    @Column(name = "score_status", nullable = false, length = 30)
    @ColumnDefault("'Não Calculado'")
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

    @Column(name = "approval_type", length = 40)
    private String approvalType;

    @Column(name = "committee_decision", length = 60)
    private String committeeDecision;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

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

    @Column(name = "is_converted_to_project", nullable = false)
    @ColumnDefault("false")
    private boolean isConvertedToProject;

    @Column(name = "is_converted_to_program", nullable = false)
    @ColumnDefault("false")
    private boolean isConvertedToProgram;

    @OneToMany(mappedBy = "demand", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<DemandParticipatingDirection> participatingDirections = new ArrayList<>();

    @Column(name = "reprioritization_reason", length = 80)
    private String reprioritizationReason;

    @Column(name = "reprioritization_justification", columnDefinition = "TEXT")
    private String reprioritizationJustification;

    @Column(name = "reprioritized_at")
    private Instant reprioritizedAt;

    @Column(name = "reprioritized_by", length = 150)
    private String reprioritizedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy = "system";

    @Column(name = "updated_by", nullable = false, length = 150)
    private String updatedBy = "system";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @JsonProperty("strategicPlanId")
    public UUID getStrategicPlanId() {
        return strategicPlan != null ? strategicPlan.getId() : null;
    }

    @JsonProperty("operationalPlanId")
    public UUID getOperationalPlanId() {
        return operationalPlan != null ? operationalPlan.getId() : null;
    }

    @JsonProperty("strategicPillarId")
    public UUID getStrategicPillarId() {
        return strategicPillar != null ? strategicPillar.getId() : null;
    }

    @JsonProperty("strategicObjectiveId")
    public UUID getStrategicObjectiveId() {
        return strategicObjective != null ? strategicObjective.getId() : null;
    }

    @JsonProperty("programId")
    public UUID getProgramId() {
        return program != null ? program.getId() : null;
    }

    @JsonProperty("committeeId")
    public UUID getCommitteeId() {
        return getResponsibleCommitteeId();
    }

    @JsonProperty("responsibleCommitteeId")
    public UUID getResponsibleCommitteeId() {
        return responsibleCommittee != null ? responsibleCommittee.getId() : null;
    }

    @JsonProperty("typeId")
    public UUID getTypeId() {
        return type != null ? type.getId() : null;
    }

    @JsonProperty("domainId")
    public UUID getDomainId() {
        return domain != null ? domain.getId() : null;
    }

    @JsonProperty("convertedProjectId")
    public UUID getConvertedProjectId() {
        return convertedProject != null ? convertedProject.getId() : null;
    }

    @JsonProperty("convertedProgramId")
    public UUID getConvertedProgramId() {
        return convertedProgram != null ? convertedProgram.getId() : null;
    }
}
