package cvt.cv.ppmbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "capacity_settings")
@Getter
@Setter
@NoArgsConstructor
public class CapacitySettings extends BaseEntity {
    @Column(name = "max_concurrent_projects", nullable = false)
    private Integer maxConcurrentProjects = 3;
}
