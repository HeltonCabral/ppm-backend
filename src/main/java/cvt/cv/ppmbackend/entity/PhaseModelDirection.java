package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "phase_model_directions", uniqueConstraints = @UniqueConstraint(
        name = "uk_phase_model_directions_model_code", columnNames = { "phase_model_id", "direction_code" }))
@Getter
@Setter
@NoArgsConstructor
public class PhaseModelDirection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_model_id", nullable = false)
    @JsonIgnore
    private PhaseModel phaseModel;

    @Column(name = "direction_code", nullable = false, length = 60)
    private String directionCode;

    @Column(name = "direction_name", length = 120)
    private String directionName;
}
