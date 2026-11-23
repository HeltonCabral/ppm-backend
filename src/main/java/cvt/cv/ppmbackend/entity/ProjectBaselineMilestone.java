package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_baseline_milestone")
@Getter
@Setter
@NoArgsConstructor
public class ProjectBaselineMilestone extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_id", nullable = false)
    @JsonIgnore
    private ProjectBaseline baseline;
    @Column(name = "milestone_id")
    private UUID milestoneId;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(name = "due_date")
    private LocalDate dueDate;
}
