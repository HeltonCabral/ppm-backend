package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

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

    @Column(nullable = false)
    private Boolean active = true;
}
