package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "macro_resources")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class MacroResource extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCapacity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CapacityUnit capacityUnit;
}
