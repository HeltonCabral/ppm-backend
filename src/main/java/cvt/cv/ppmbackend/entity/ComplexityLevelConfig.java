package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.DemandComplexity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "complexity_level_configs")
@Getter
@Setter
@NoArgsConstructor
public class ComplexityLevelConfig {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemandComplexity level;

    @Column(name = "min_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal minScore;
    @Column(name = "max_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal maxScore;
    @Column(name = "estimated_duration_months", nullable = false)
    private Integer estimatedDurationMonths;
}
