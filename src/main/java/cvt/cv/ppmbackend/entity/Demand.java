package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "demands", indexes = {
        @Index(name = "idx_demands_code", columnList = "code", unique = true),
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

    @Column(length = 1000)
    private String summary;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @ColumnDefault("'NORMAL'")
    private DemandNature nature = DemandNature.NORMAL;

    @Column(length = 150)
    private String sponsor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = true)
    private PhaseModel  type;

    @Column(nullable = false, length = 80)
    private String origin = "MANUAL";

    @Column(name = "easy_vista_ref", length = 120)
    private String easyVistaRef;

    @ManyToMany
    @JoinTable(
            name = "demand_suppliers",
            joinColumns = @JoinColumn(name = "demand_id"),
            inverseJoinColumns = @JoinColumn(name = "supplier_id"))
    private Set<Supplier> suppliers = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "domain_id")
    private LookupValue domain;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "budget_line_id")
    private LookupValue budgetLine;


    @Column(name = "impacted_system", length = 150)
    private String impactedSystem;

    @Column(name = "current_evidence", columnDefinition = "TEXT")
    private String currentEvidence;

    @Column(name = "problem_statement", columnDefinition = "TEXT", nullable = false)
    private String problemStatement;

    @Column(name = "desired_outcome", columnDefinition = "TEXT", nullable = false)
    private String desiredOutcome;

    @Column(name = "proposed_response", columnDefinition = "TEXT")
    private String proposedResponse;

    @Column(length = 40)
    private String urgency;

    @Column(name = "estimated_budget", precision = 19, scale = 2)
    private BigDecimal estimatedBudget;

    @Column(name = "desired_date")
    private LocalDate desiredDate;

    @Column(name = "mandatory_deadline")
    private LocalDate mandatoryDeadline;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "risks_identified", columnDefinition = "TEXT")
    private String risksIdentified;

    @Column(name = "dependencies_identified", columnDefinition = "TEXT")
    private String dependenciesIdentified;

    @Column(name = "directions_count")
    private Integer directionsCount;

    @Column(name = "profiles_count")
    private Integer profilesCount;

    @Column(name = "total_resources")
    private Integer totalResources;

    @Column(name = "dependencies_count")
    private Integer dependenciesCount;

    @Column(name = "complexity_score", precision = 4, scale = 2)
    private BigDecimal complexityScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DemandComplexity complexity;

    @Column(name = "estimated_duration_months")
    private Integer estimatedDurationMonths;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @OneToMany(mappedBy = "demand", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<DemandPlanningCycle> planningCycles = new ArrayList<>();

    @OneToMany(mappedBy = "demand", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<DemandParticipatingDirection> participatingDirections = new ArrayList<>();

    /**
     * Planeamento personalizado da Demanda. Quando contém fases, substitui o planeamento do Tipo
     * (fases + recursos) sem alterar o Tipo. Vazio quando a Demanda segue o Tipo.
     */
    @OneToMany(mappedBy = "demand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    @JsonIgnore
    private List<DemandCustomPhase> customPhases = new ArrayList<>();

    /** Tipo de origem do planeamento personalizado (informativo). Nulo quando segue o Tipo. */
    @Column(name = "custom_planning_base_type_id")
    private UUID customPlanningBaseTypeId;

    @OneToMany(mappedBy = "demand", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<DemandDependency> dependencies = new ArrayList<>();

    @OneToMany(mappedBy = "demand", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<DemandSuccessCriterion> successCriteria = new ArrayList<>();

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

    @JsonProperty("typeId")
    public UUID getTypeId() {
        return type != null ? type.getId() : null;
    }

    @JsonProperty("domainId")
    public UUID getDomainId() {
        return domain != null ? domain.getId() : null;
    }

    @JsonProperty("budgetLineId")
    public UUID getBudgetLineId() {
        return budgetLine != null ? budgetLine.getId() : null;
    }
}
