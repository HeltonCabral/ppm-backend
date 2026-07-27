package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Supplier extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 150)
    private String contract;
    @Column(precision = 12, scale = 2)
    private BigDecimal availableHourPackage;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal consumedHours = BigDecimal.ZERO;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
}
