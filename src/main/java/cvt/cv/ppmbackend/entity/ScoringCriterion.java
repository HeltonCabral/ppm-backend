package cvt.cv.ppmbackend.entity;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scoring_criteria", indexes = {
    @Index(name = "idx_scoring_criteria_dimension", columnList = "dimension_id"),
        @Index(name = "idx_scoring_criteria_active", columnList = "active"),
        @Index(name = "idx_scoring_criteria_order", columnList = "order_index")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ScoringCriterion extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String label;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "dimension_id", nullable = false)
    @JsonIgnoreProperties("criteria")
    private ScoringDimension dimension;

    @Column(name = "min_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal minScore;

    @Column(name = "max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Boolean active = true;
}
