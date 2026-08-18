package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "project_executions")
@Getter
@Setter
@NoArgsConstructor
public class ProjectExecution extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    @JsonIgnore
    private Project project;
    @Column(nullable = false)
    private Integer progress = 0;
    @Column(name = "consumed_budget", nullable = false, precision = 18, scale = 2)
    private BigDecimal consumedBudget = BigDecimal.ZERO;
    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;
    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;
    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;
    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_status", length = 20)
    private ExecutiveStatus scheduleStatus = ExecutiveStatus.GREEN;
    @Enumerated(EnumType.STRING)
    @Column(name = "cost_status", length = 20)
    private ExecutiveStatus costStatus = ExecutiveStatus.GREEN;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_status", length = 20)
    private ExecutiveStatus riskStatus = ExecutiveStatus.GREEN;
    @Enumerated(EnumType.STRING)
    @Column(name = "value_status", length = 20)
    private ExecutiveStatus valueStatus = ExecutiveStatus.GREEN;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RiskLevel risk = RiskLevel.LOW;
    @Column(name = "delay_reasons", columnDefinition = "TEXT")
    private String delayReasons;
    @Column(name = "execution_notes", columnDefinition = "TEXT")
    private String executionNotes;
    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;
    @Column(name = "last_updated_by", length = 150)
    private String lastUpdatedBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
