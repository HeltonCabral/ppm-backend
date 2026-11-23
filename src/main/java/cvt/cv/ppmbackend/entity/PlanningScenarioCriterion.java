package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ScenarioCriterionCode;
import cvt.cv.ppmbackend.enums.ScenarioCriterionOperator;
import cvt.cv.ppmbackend.enums.ScenarioCriterionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Critério dinâmico de composição de um cenário. Serve apenas para propor a composição:
 * nunca altera o score oficial da Demanda.
 */
@Entity
@Table(name = "planning_scenario_criteria", indexes = {
        @Index(name = "idx_scenario_criteria_scenario", columnList = "scenario_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PlanningScenarioCriterion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    @JsonIgnore
    private PlanningScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_code", nullable = false, length = 60)
    private ScenarioCriterionCode criterionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScenarioCriterionOperator operator;

    /** Valor do critério. Para IN/NOT_IN/BETWEEN contém a lista separada por vírgulas. */
    @Column(name = "criterion_value", nullable = false, length = 2000)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_type", nullable = false, length = 20)
    private ScenarioCriterionType criterionType = ScenarioCriterionType.FILTER;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
