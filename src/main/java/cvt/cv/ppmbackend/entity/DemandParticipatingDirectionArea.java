package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.AreaRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "demand_participating_direction_areas", indexes = {
        @Index(name = "idx_dpda_participating_direction_id", columnList = "participating_direction_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandParticipatingDirectionArea extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participating_direction_id", nullable = false)
    @JsonIgnore
    private DemandParticipatingDirection participatingDirection;

    @Column(name = "area_code", nullable = false, length = 60)
    private String areaCode;

    @Column(name = "area_name", length = 120)
    private String areaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AreaRole role;

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
