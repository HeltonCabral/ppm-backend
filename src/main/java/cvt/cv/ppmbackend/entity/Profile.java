package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.ProfileCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
public class Profile extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "direction_code", length = 50)
    private String directionCode;
    @Column(name = "direction_name", length = 50)
    private String directionName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "available_capacity", nullable = false)
    private Integer availableCapacity;

    @Column(name = "simultaneous_demand_capacity", nullable = false)
    private Integer simultaneousDemandCapacity;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
