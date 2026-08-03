package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "demands", indexes = {
        @Index(name = "idx_demands_code", columnList = "code", unique = true),
        @Index(name = "idx_demands_status", columnList = "status"),
        @Index(name = "idx_demands_origin", columnList = "origin"),
        @Index(name = "idx_demands_created_at", columnList = "created_at")
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
    private String area;
    @Column(length = 120)
    private String direction;
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
    private String status = "IN_ANALYSYS";
    @Column(name = "capacity_status", length = 40)
    private String capacityStatus;
    @Column(name = "risk_status", length = 40)
    private String riskStatus;
    @Column(name = "risks_identified", columnDefinition = "TEXT")
    private String risksIdentified;
    @Column(name = "dependencies_identified", columnDefinition = "TEXT")
    private String dependenciesIdentified;
    @Column(name = "score_value", precision = 19, scale = 2)
    private BigDecimal scoreValue;
    @Column(name = "score_effort", precision = 19, scale = 2)
    private BigDecimal scoreEffort;
    @Column(name = "score_risk", precision = 19, scale = 2)
    private BigDecimal scoreRisk;
    @Column(name = "score_total", precision = 19, scale = 2)
    private BigDecimal scoreTotal;
    @Column(name = "portfolio_rank")
    private Integer portfolioRank;
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
}
