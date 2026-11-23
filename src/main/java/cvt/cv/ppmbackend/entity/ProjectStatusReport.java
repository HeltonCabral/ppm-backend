package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Relatório de estado periódico. Imutável: um engano corrige-se com um relatório novo. */
@Entity
@Table(name = "project_status_report",
        indexes = @Index(name = "idx_project_status_report_project_id", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectStatusReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    @JsonIgnore
    private Project project;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false, length = 10, updatable = false)
    private ExecutiveStatus overallStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_status", nullable = false, length = 10, updatable = false)
    private ExecutiveStatus scheduleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_status", nullable = false, length = 10, updatable = false)
    private ExecutiveStatus costStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_status", nullable = false, length = 10, updatable = false)
    private ExecutiveStatus scopeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_status", nullable = false, length = 10, updatable = false)
    private ExecutiveStatus riskStatus;

    @Column(nullable = false, updatable = false)
    private Integer progress;

    @Column(name = "consumed_budget", precision = 18, scale = 2, updatable = false)
    private BigDecimal consumedBudget;

    @Column(nullable = false, length = 4000, updatable = false)
    private String summary;

    @Column(length = 4000, updatable = false)
    private String achievements;

    @Column(name = "next_steps", length = 4000, updatable = false)
    private String nextSteps;

    @Column(length = 4000, updatable = false)
    private String blockers;

    @Column(name = "needs_support", nullable = false, updatable = false)
    private boolean needsSupport;

    @Column(name = "created_by", length = 150, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
