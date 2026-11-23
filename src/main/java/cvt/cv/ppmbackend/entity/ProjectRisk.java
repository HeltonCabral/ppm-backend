package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ProjectRiskKind;
import cvt.cv.ppmbackend.enums.ProjectRiskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** Risco (pode acontecer) ou problema (já aconteceu) do projeto. */
@Entity
@Table(name = "project_risk", indexes = @Index(name = "idx_project_risk_project_id", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectRisk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProjectRiskKind kind;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 1–5; num ISSUE é sempre 5. */
    @Column(nullable = false)
    private Integer probability;

    /** 1–5. */
    @Column(nullable = false)
    private Integer impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectRiskStatus status = ProjectRiskStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String response;

    @Column(length = 150)
    private String owner;

    @Column(name = "due_date")
    private LocalDate dueDate;

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
