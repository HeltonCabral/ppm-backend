package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fase de um planeamento personalizado de uma Demanda. Espelha {@link PhaseDefinition} (fase do
 * Tipo), mas pertence à Demanda e substitui o planeamento do Tipo quando presente.
 */
@Entity
@Table(name = "demand_custom_phases")
@Getter
@Setter
@NoArgsConstructor
public class DemandCustomPhase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    /** Fase do Tipo de origem, quando a fase personalizada deriva de uma fase do Tipo. */
    @Column(name = "source_phase_id")
    private UUID sourcePhaseId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "duration_weight_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal durationWeightPercentage;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "customPhase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    private List<DemandCustomResource> resources = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
