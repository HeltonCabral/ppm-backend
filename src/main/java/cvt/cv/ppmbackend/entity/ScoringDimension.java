package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.ScoringImpactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scoring_dimensions", uniqueConstraints = @UniqueConstraint(name = "uk_scoring_dimension_code", columnNames = {
        "code" }), indexes = {
                @Index(name = "idx_scoring_dimension_active", columnList = "active")
        })
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ScoringDimension extends BaseEntity {
    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal weight;

        @Enumerated(EnumType.STRING)
        @Column(name = "impact_type", nullable = false, length = 20)
        private ScoringImpactType impactType = ScoringImpactType.BENEFIT;

    @OneToMany(mappedBy = "dimension")
    @OrderBy("orderIndex ASC")
    private List<ScoringCriterion> criteria = new ArrayList<>();

    @Column(nullable = false)
    private Boolean active = true;
}
