package cvt.cv.ppmbackend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "phase_definitions", uniqueConstraints = @UniqueConstraint(
        name = "uk_phase_definitions_model_code", columnNames = { "phase_model_id", "code" }))
@Getter
@Setter
@NoArgsConstructor
public class PhaseDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_model_id", nullable = false)
    private PhaseModel phaseModel;

    @Column(nullable = false, length = 50)
    private String code;

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

    @OneToMany(mappedBy = "phaseDefinition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PhaseProfile> profiles = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
