package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.InclusionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Posição de uma Demanda dentro de um cenário. A existência do item não altera o
 * DemandPlanningStatus — só a seleção do cenário como FINAL o faz.
 */
@Entity
@Table(name = "planning_scenario_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scenario_item_cycle",
                columnNames = { "scenario_id", "planning_cycle_id" }),
        indexes = {
                @Index(name = "idx_scenario_items_scenario", columnList = "scenario_id"),
                @Index(name = "idx_scenario_items_cycle", columnList = "planning_cycle_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class PlanningScenarioItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    @JsonIgnore
    private PlanningScenario scenario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_cycle_id", nullable = false)
    @JsonIgnore
    private DemandPlanningCycle demandPlanningCycle;

    @Column(nullable = false)
    private boolean included = true;

    @Column(name = "scenario_rank")
    private Integer scenarioRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "inclusion_source", nullable = false, length = 20)
    private InclusionSource inclusionSource = InclusionSource.MANUAL;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
