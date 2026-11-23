package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Recurso (perfil + alocação) de uma fase de planeamento personalizado de uma Demanda. Espelha
 * {@link PhaseProfile}, mas pertence a uma {@link DemandCustomPhase}.
 */
@Entity
@Table(name = "demand_custom_resources")
@Getter
@Setter
@NoArgsConstructor
public class DemandCustomResource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "custom_phase_id", nullable = false)
    @JsonIgnore
    private DemandCustomPhase customPhase;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "allocation_percentage", nullable = false, precision = 7, scale = 2)
    private BigDecimal allocationPercentage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
