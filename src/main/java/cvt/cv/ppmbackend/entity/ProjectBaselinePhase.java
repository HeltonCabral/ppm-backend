package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_baseline_phase")
@Getter
@Setter
@NoArgsConstructor
public class ProjectBaselinePhase extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_id", nullable = false)
    @JsonIgnore
    private ProjectBaseline baseline;
    @Column(name = "phase_id")
    private UUID phaseId;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false)
    private Integer sequence;
    @Column(precision = 5, scale = 2)
    private BigDecimal weight;
    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;
    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;
}
