package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "demand_success_criteria", indexes = {
        @Index(name = "idx_demand_success_criteria_demand_id", columnList = "demand_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandSuccessCriterion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
