package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import cvt.cv.ppmbackend.enums.ProgramStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Program extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(length = 120)
    private String programManager;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProgramStatus status;

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

    @Column(name = "direction_name", length = 120)
    private String directionName;

    @Column(name = "direction_code", length = 60)
    private String directionCode;

    @Column(name = "area_name", length = 120)
    private String areaName;

    @Column(name = "area_code", length = 60)
    private String areaCode;

    @Column(name = "estimated_budget", precision = 19, scale = 2)
    private BigDecimal estimatedBudget;

    @Column(name = "source_demand_id")
    private UUID sourceDemandId;

    @Column(name = "source_demand_portfolio_rank")
    private Integer sourceDemandPortfolioRank;

    @Column(name = "created_from_conditional_plan_approval")
    private Boolean createdFromConditionalPlanApproval;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "program_strategic_objectives", joinColumns = @JoinColumn(name = "program_id"), inverseJoinColumns = @JoinColumn(name = "strategic_objective_id"))
    private Set<StrategicObjective> strategicObjectives = new HashSet<>();
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "program_suppliers", joinColumns = @JoinColumn(name = "program_id"), inverseJoinColumns = @JoinColumn(name = "supplier_id"))
    private Set<Supplier> suppliers = new HashSet<>();

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
