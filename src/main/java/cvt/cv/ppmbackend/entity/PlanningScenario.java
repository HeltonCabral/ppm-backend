package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cenário de composição do portfólio. Vários cenários DRAFT podem coexistir no mesmo ciclo,
 * mas apenas um pode ser FINAL — é esse que determina o status das Demandas.
 */
@Entity
@Table(name = "planning_scenarios", indexes = {
        @Index(name = "idx_planning_scenarios_plan", columnList = "strategic_plan_id"),
        @Index(name = "idx_planning_scenarios_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class PlanningScenario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategic_plan_id", nullable = false)
    @JsonIgnore
    private StrategicPlan strategicPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_plan_id")
    @JsonIgnore
    private OperationalPlan operationalPlan;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScenarioStatus status = ScenarioStatus.DRAFT;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scenarioRank ASC")
    @JsonIgnore
    private List<PlanningScenarioItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @JsonIgnore
    private List<PlanningScenarioCriterion> criteria = new ArrayList<>();

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy = "system";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "selected_by", length = 150)
    private String selectedBy;

    @Column(name = "selected_at")
    private Instant selectedAt;
}
