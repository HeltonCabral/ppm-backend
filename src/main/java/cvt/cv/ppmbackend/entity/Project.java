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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_plan_id")
    @JsonIgnore
    private OperationalPlan operationalPlan;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "domain_id")
    private LookupValue domain;
    @Column(nullable = false, length = 120)
    private String businessArea;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_type_id")
    private LookupValue projectType;
    @Column(length = 120)
    private String responsibleDirection;
    @Column(length = 120)
    private String responsibleTeam;
    @Column(nullable = false, length = 120)
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
    @Column(nullable = false)
    private ExecutiveStatus scheduleStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutiveStatus costStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutiveStatus riskStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutiveStatus valueStatus;
    @Column(columnDefinition = "TEXT")
    private String expectedBenefits;
    private LocalDate plannedStartDate;
    private LocalDate startDate;
    private LocalDate plannedEndDate;
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;
    private Integer ranking;
    @Column(length = 120)
    private String budgetLine;
    @Column(precision = 18, scale = 2)
    private BigDecimal budget;
    @Enumerated(EnumType.STRING)
    private PlanType planType;
    @Column(columnDefinition = "TEXT")
    private String delayReasons;
    @Column(name = "source_demand_id")
    private UUID sourceDemandId;

    @JsonProperty("operationalPlanId")
    public java.util.UUID getOperationalPlanId() {
        return operationalPlan != null ? operationalPlan.getId() : null;
    }
}
