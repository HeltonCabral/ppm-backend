package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import cvt.cv.ppmbackend.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Project extends BaseEntity {
    @Column(unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @ColumnDefault("'EXTRA_PLAN'")
    private ProjectOrigin origin = ProjectOrigin.EXTRA_PLAN;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_demand_id")
    @JsonIgnore
    private Demand sourceDemand;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;
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
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "domain_id")
    private LookupValue domain;
    @Column(length = 120)
    private String businessArea;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_type_id")
    private LookupValue projectType;
    @Column(name = "direction_name", length = 200)
    private String directionName;
    @Column(name = "direction_code", length = 100)
    private String directionCode;
    @Column(name = "area_name", length = 200)
    private String areaName;
    @Column(name = "area_code", length = 100)
    private String areaCode;
    @Column(length = 120)
    private String responsibleDirection;
    @Column(length = 120)
    private String responsibleTeam;
    @Column(length = 120)
    private String projectManager;
    @Column(name = "project_manager_id")
    private UUID projectManagerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_phase_id")
    private LookupValue projectPhase;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_supplier_id")
    private Supplier mainSupplier;
    @ManyToMany
    @JoinTable(name = "project_suppliers", joinColumns = @JoinColumn(name = "project_id"), inverseJoinColumns = @JoinColumn(name = "supplier_id"))
    private Set<Supplier> suppliers = new HashSet<>();
    @Column(length = 150)
    private String impactedSystem;
    @Column(name = "expected_impact", columnDefinition = "TEXT")
    private String expectedImpact;
    @Column(name = "expected_benefit", columnDefinition = "TEXT")
    private String expectedBenefit;
    @Column(columnDefinition = "TEXT")
    private String expectedBenefits;
    @Enumerated(EnumType.STRING)
    private Priority priority;
    @Column(name = "execution_rank")
    private Integer executionRank;
    @Column(length = 120)
    private String budgetLine;
    @Column(precision = 18, scale = 2)
    private BigDecimal budget;
    @Column(name = "estimated_budget", precision = 19, scale = 2)
    private BigDecimal estimatedBudget;
    @Enumerated(EnumType.STRING)
    private PlanType planType;
    @Column(name = "source_demand_portfolio_rank")
    private Integer sourceDemandPortfolioRank;
    @Column(name = "created_from_conditional_plan_approval")
    private Boolean createdFromConditionalPlanApproval;
    @Column(name = "extra_plan_justification", columnDefinition = "TEXT")
    private String extraPlanJustification;
    @OneToOne(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private ProjectExecution execution;
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectTeamMember> teamMembers = new ArrayList<>();
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @Column(name = "created_by", length = 150)
    private String createdBy = "system";
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "updated_by", length = 150)
    private String updatedBy = "system";

    @JsonProperty("programId")
    public UUID getProgramId() {
        return program != null ? program.getId() : null;
    }

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

    @JsonProperty("domainId")
    public UUID getDomainId() {
        return domain != null ? domain.getId() : null;
    }

    @JsonProperty("sourceDemandId")
    public UUID getSourceDemandId() {
        return sourceDemand != null ? sourceDemand.getId() : null;
    }

    @JsonProperty("supplierId")
    public UUID getSupplierId() {
        return mainSupplier != null ? mainSupplier.getId() : null;
    }

    public void attachExecution(ProjectExecution value) {
        execution = value;
        if (value != null && value.getProject() != this) {
            value.setProject(this);
        }
    }
}
