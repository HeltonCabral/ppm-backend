package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.YearMonth;

@Entity
@Table(name = "capacity_allocations", uniqueConstraints = @UniqueConstraint(columnNames = { "project_id",
        "macro_resource_id", "period" }))
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class CapacityAllocation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "macro_resource_id", nullable = false)
    private MacroResource macroResource;
    @Column(nullable = false, length = 7)
    private YearMonth period;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedCapacity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CapacityUnit capacityUnit;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllocationStatus allocationStatus;
}
