package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "demand_profile_requirements")
@Getter
@Setter
@NoArgsConstructor
public class DemandProfileRequirement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    @JsonIgnore
    private Demand demand;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "required_quantity", nullable = false)
    private Integer requiredQuantity;

    /**
     * Carga equivalente em FTE. Preenchida quando o requisito é derivado dos perfis das fases do
     * Tipo (allocationPercentage / 100). Para requisitos legados persistidos fica nula e a carga
     * equivalente deriva de {@code requiredQuantity}.
     */
    @Transient
    private BigDecimal equivalentLoad;

    public BigDecimal getEquivalentLoad() {
        return equivalentLoad != null
                ? equivalentLoad
                : BigDecimal.valueOf(requiredQuantity == null ? 0 : requiredQuantity);
    }

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "demand_profile_requirement_phases",
            joinColumns = @JoinColumn(name = "requirement_id"),
            inverseJoinColumns = @JoinColumn(name = "phase_id"))
    private Set<PhaseDefinition> phases = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
