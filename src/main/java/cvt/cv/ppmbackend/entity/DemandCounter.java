package cvt.cv.ppmbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "demand_counters")
@Getter
@Setter
@NoArgsConstructor
public class DemandCounter {
    @Id
    @Column(name = "counter_key", nullable = false, length = 80)
    private String counterKey;
    @Column(name = "counter_value", nullable = false)
    private Long counterValue;
}
