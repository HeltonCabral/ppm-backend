package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cvt.cv.ppmbackend.enums.RateUnit;
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
   

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal ratePerHour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RateUnit rateUnit;

    @ManyToOne
    @JoinColumn(name = "currency_id")
    private Currency currency;


  
   
    
}
