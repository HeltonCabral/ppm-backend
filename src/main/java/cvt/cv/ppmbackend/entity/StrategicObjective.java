package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "strategic_objectives")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class StrategicObjective extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;
    @Column(name = "start_year")
    private Integer startYear;
    @Column(name = "end_year")
    private Integer endYear;
    @Column(length = 100)
    private String perspective;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "strategic_element_id", nullable = false)
    private StrategicElement strategicElement;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_plan_id")
    @JsonIgnore
    private StrategicPlan strategicPlan;
    @OneToMany(mappedBy = "strategicObjective", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties({ "strategicObjective", "hibernateLazyInitializer", "handler" })
    private List<StrategicObjectiveAnnualTarget> annualTargets = new ArrayList<>();
    @OneToMany(mappedBy = "strategicObjective", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Kpi> kpis = new ArrayList<>();

    

    @JsonProperty("strategicPlanId")
    public UUID getStrategicPlanId() {
        return strategicPlan != null ? strategicPlan.getId() : null;
    }
}
