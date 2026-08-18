package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ReprioritizationReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project_execution_rank_history", indexes =
        @Index(name = "idx_project_execution_rank_history_project", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectExecutionRankHistory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;
    @Column(name = "previous_rank")
    private Integer previousRank;
    @Column(name = "new_rank", nullable = false)
    private Integer newRank;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private ReprioritizationReason reason;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String justification;
    @Column(name = "changed_by", nullable = false, length = 150)
    private String changedBy;
    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
