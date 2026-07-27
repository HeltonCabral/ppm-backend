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
@Table(name = "strategic_pillars")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class StrategicPillar extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(length = 100)
    private String icon;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategic_plan_id")
    @JsonIgnore
    private StrategicPlan strategicPlan;
    @OneToMany(mappedBy = "strategicPillar", fetch = FetchType.EAGER)
    @JsonIgnoreProperties({ "strategicPillar", "hibernateLazyInitializer", "handler" })
    private List<StrategicElement> strategicElements = new ArrayList<>();

    @JsonProperty("strategicPlanId")
    public UUID getStrategicPlanId() {
        return strategicPlan != null ? strategicPlan.getId() : null;
    }
}
