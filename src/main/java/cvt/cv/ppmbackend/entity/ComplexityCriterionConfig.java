package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.ComplexityCriterion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "complexity_criterion_configs", uniqueConstraints =
        @UniqueConstraint(name = "uk_complexity_criterion_configs_criterion", columnNames = "criterion"))
@Getter
@Setter
@NoArgsConstructor
public class ComplexityCriterionConfig extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ComplexityCriterion criterion;

    @Column(name = "low_min", nullable = false)
    private Integer lowMin;
    @Column(name = "low_max", nullable = false)
    private Integer lowMax;
    @Column(name = "medium_min", nullable = false)
    private Integer mediumMin;
    @Column(name = "medium_max", nullable = false)
    private Integer mediumMax;
    @Column(name = "high_min", nullable = false)
    private Integer highMin;
    @Column(name = "high_max", nullable = false)
    private Integer highMax;
    @Column(name = "very_high_min", nullable = false)
    private Integer veryHighMin;
    @Column(name = "very_high_max")
    private Integer veryHighMax;
    @Column(nullable = false)
    private boolean active = true;
}
