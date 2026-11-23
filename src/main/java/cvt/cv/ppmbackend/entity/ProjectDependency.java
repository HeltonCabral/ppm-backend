package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ProjectDependencyType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project_dependency", indexes = {
        @Index(name = "idx_project_dependency_predecessor", columnList = "predecessor_id"),
        @Index(name = "idx_project_dependency_successor", columnList = "successor_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ProjectDependency extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "predecessor_id", nullable = false)
    @JsonIgnore
    private Project predecessor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "successor_id", nullable = false)
    @JsonIgnore
    private Project successor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ProjectDependencyType type;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    @JsonIgnore
    private ProjectMilestone milestone;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @Column(name = "created_by", length = 150)
    private String createdBy;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "updated_by", length = 150)
    private String updatedBy;
}
