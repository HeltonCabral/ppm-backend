package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cvt.cv.ppmbackend.enums.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dependencies")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Dependency extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DependencyType type;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_project_id", nullable = false)
    private Project sourceProject;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_project_id")
    private Project targetProject;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DependencyStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImpactLevel impactLevel;
}
