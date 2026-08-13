package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import cvt.cv.ppmbackend.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Project extends BaseEntity {
    @Column(nullable = false, length = 180)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
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
    @Column(name = "direction_name", length = 120)
    private String directionName;
    @Column(name = "direction_code", length = 60)
    private String directionCode;
    @Column(name = "area_name", length = 120)
    private String areaName;
    @Column(name = "area_code", length = 60)
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
    @Enumerated(EnumType.STRING)
    private ExecutiveStatus scheduleStatus;
    @Enumerated(EnumType.STRING)
    private ExecutiveStatus costStatus;
    @Enumerated(EnumType.STRING)
    private ExecutiveStatus riskStatus;
    @Enumerated(EnumType.STRING)
    private ExecutiveStatus valueStatus;
    @Column(name = "expected_impact", columnDefinition = "TEXT")
    private String expectedImpact;
    @Column(name = "expected_benefit", columnDefinition = "TEXT")
    private String expectedBenefit;
    @Column(columnDefinition = "TEXT")
    private String expectedBenefits;
    private LocalDate plannedStartDate;
    private LocalDate startDate;
    private LocalDate plannedEndDate;
    private LocalDate endDate;
    @Column(name = "desired_date")
    private LocalDate desiredDate;
    @Enumerated(EnumType.STRING)
    private Priority priority;
    private Integer ranking;
    @Column(name = "portfolio_rank")
    private Integer portfolioRank;
    @Column(length = 120)
    private String budgetLine;
    @Column(precision = 18, scale = 2)
    private BigDecimal budget;
    @Column(name = "estimated_budget", precision = 19, scale = 2)
    private BigDecimal estimatedBudget;
    @Enumerated(EnumType.STRING)
    private PlanType planType;
    @Column(columnDefinition = "TEXT")
    private String delayReasons;
    @Column(name = "source_demand_id")
    private UUID sourceDemandId;
    @Column(name = "source_demand_portfolio_rank")
    private Integer sourceDemandPortfolioRank;
    @Column(name = "created_from_conditional_plan_approval")
    private Boolean createdFromConditionalPlanApproval;

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
}
