package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "strategic_elements")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class StrategicElement extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(length = 100)
    private String icon;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "strategic_pillar_id", nullable = false)
    private StrategicPillar strategicPillar;
    @OneToMany(mappedBy = "strategicElement", fetch = FetchType.EAGER)
    @JsonIgnoreProperties({ "strategicElement", "hibernateLazyInitializer", "handler" })
    private List<StrategicObjective> strategicObjectives = new ArrayList<>();
}
