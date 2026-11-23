package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.SupplierCostMode;
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

import java.math.BigDecimal;

/**
 * A supplier on a demand with its estimated cost: the hours (or days) at the supplier's rate, or
 * a closed value in the supplier's currency. The demand's {@code suppliers} keep only the link.
 */
@Entity
@Table(name = "demand_supplier_lines", indexes = {
        @Index(name = "idx_demand_supplier_lines_demand_id", columnList = "demand_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandSupplierLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SupplierCostMode mode;

    /** Hours/days (HOURS) or the value (VALUE). Null: not known yet (a link saved before the lines). */
    @Column(precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
