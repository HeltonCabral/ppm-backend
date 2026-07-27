package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "strategic_plans", uniqueConstraints = @UniqueConstraint(name = "uk_cycle_name_period", columnNames = {
        "name", "start_year", "end_year" }), indexes = { @Index(name = "idx_cycle_status", columnList = "status"),
                @Index(name = "idx_cycle_period", columnList = "start_year,end_year") })
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class StrategicPlan extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "start_year", nullable = false)
    private Integer startYear;
    @Column(name = "end_year", nullable = false)
    private Integer endYear;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StrategicPlanStatus status = StrategicPlanStatus.DRAFT;
    @Column(nullable = false)
    private Integer revision = 1;
    @Version
    private Long version;
    @Column(name = "approval_date")
    private LocalDate approvalDate;
    @Column(name = "approved_by", length = 150)
    private String approvedBy;
    @Column(columnDefinition = "TEXT")
    private String description;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy = "system";
    @Column(name = "updated_by", nullable = false, length = 150)
    private String updatedBy = "system";
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
