package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kpis")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "strategicObjective" })
public class Kpi extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 50)
    private String target;
    private Double current;
    private Double goal;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategic_objective_id", nullable = false)
    private StrategicObjective strategicObjective;
    @OneToMany(mappedBy = "kpi", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties({ "kpi", "hibernateLazyInitializer", "handler" })
    private List<KpiMeasurement> measurements = new ArrayList<>();
}
