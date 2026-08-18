package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "demand_dependencies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_demand_dependencies_demand_target",
                columnNames = { "demand_id", "depends_on_demand_id" }),
        indexes = {
                @Index(name = "idx_demand_dependencies_demand_id", columnList = "demand_id"),
                @Index(name = "idx_demand_dependencies_target_id", columnList = "depends_on_demand_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class DemandDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depends_on_demand_id", nullable = false)
    @JsonIgnore
    private Demand dependsOnDemand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemandDependencyType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 150)
    private String createdBy = "system";
}
