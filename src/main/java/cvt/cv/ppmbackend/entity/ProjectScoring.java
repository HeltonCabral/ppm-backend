package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "project_scorings", uniqueConstraints = @UniqueConstraint(columnNames = "project_id"))
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ProjectScoring extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @Column(nullable = false)
    private Integer strategicAlignment;
    @Column(nullable = false)
    private Integer roi;
    @Column(nullable = false)
    private Integer urgency;
    @Column(nullable = false)
    private Integer technicalComplexity;
    @Column(nullable = false)
    private Integer resourceAvailability;
    @Column(nullable = false)
    private Integer estimatedDuration;
    @Column(nullable = false)
    private Integer technologyRisk;
    @Column(nullable = false)
    private Integer dependencyRisk;
    @Column(nullable = false)
    private Integer adoptionRisk;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal finalScore;
}
