package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "demand_participating_directions", indexes = {
        @Index(name = "idx_demand_participating_directions_demand_id", columnList = "demand_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandParticipatingDirection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    @Column(name = "direction_name",length = 120)
    private String directionName;

    @Column(name = "direction_code", nullable = false, length = 60)
    private String directionCode;

    @Column(name = "area_code", length = 60)
    private String areaCode;

    private String areaName;


    @Enumerated(EnumType.STRING)
    @Column(name = "participation_type", nullable = false, length = 50)
    private DirectionParticipationType participationType;

    @Column(columnDefinition = "TEXT")
    private String observations;

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

    @Version
    private Long version;
}
