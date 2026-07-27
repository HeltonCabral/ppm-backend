package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "benefits")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Benefit extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenefitType benefitType;
    @Column(precision = 18, scale = 2)
    private BigDecimal expectedValue;
    @Column(precision = 18, scale = 2)
    private BigDecimal realizedValue;
    @Column(nullable = false)
    private LocalDate trackingDate;
    @Column(columnDefinition = "TEXT")
    private String notes;
}
