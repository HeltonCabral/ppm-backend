package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Program extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, length = 120)
    private String programManager;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "program_strategic_objectives", joinColumns = @JoinColumn(name = "program_id"), inverseJoinColumns = @JoinColumn(name = "strategic_objective_id"))
    private Set<StrategicObjective> strategicObjectives = new HashSet<>();
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "program_suppliers", joinColumns = @JoinColumn(name = "program_id"), inverseJoinColumns = @JoinColumn(name = "supplier_id"))
    private Set<Supplier> suppliers = new HashSet<>();
}
