package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ObjectivesMet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Relatório final do projeto (1:1). As lições aprendidas ficam em {@link ProjectLessonLearned}. */
@Entity
@Table(name = "project_closure",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_closure_project", columnNames = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectClosure extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "objectives_met", length = 10)
    private ObjectivesMet objectivesMet;

    @Column(columnDefinition = "TEXT")
    private String handover;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;
}
