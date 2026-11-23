package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "project_baseline", uniqueConstraints = @UniqueConstraint(
        name = "uk_project_baseline_version", columnNames = { "project_id", "version" }))
@Getter
@Setter
@NoArgsConstructor
public class ProjectBaseline extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "captured_by", nullable = false, length = 150)
    private String capturedBy;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(precision = 18, scale = 2)
    private BigDecimal budget;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @OneToMany(mappedBy = "baseline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ProjectBaselinePhase> phases = new ArrayList<>();

    @OneToMany(mappedBy = "baseline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dueDate ASC")
    private List<ProjectBaselineMilestone> milestones = new ArrayList<>();
}
