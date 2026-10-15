package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.CapacityReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Capacidade individual de uma pessoa abaixo de 100% (tempo parcial, funções operacionais, ausências…).
 * Sem datas = permanente (no máximo um por pessoa); com datas, sobrepõe-se ao permanente nesses dias.
 */
@Entity
@Table(name = "person_capacity", indexes = @Index(name = "idx_person_capacity_user_name", columnList = "user_name"))
@Getter
@Setter
@NoArgsConstructor
public class PersonCapacity extends BaseEntity {

    /** Utilizador do AD, em minúsculas (o {@code code} dos membros internos da equipa). */
    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "full_name", length = 200)
    private String fullName;

    /** 0–99; 100 é o normal e não se regista. */
    @Column(name = "capacity_percentage", nullable = false)
    private Integer capacityPercentage;

    /** Limite próprio de projetos ativos em simultâneo; nulo usa a definição global. */
    @Column(name = "max_concurrent")
    private Integer maxConcurrent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CapacityReason reason;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    /** Sem início nem fim. */
    public boolean isPermanent() {
        return validFrom == null && validUntil == null;
    }

    public boolean covers(LocalDate day) {
        return (validFrom == null || !day.isBefore(validFrom)) && (validUntil == null || !day.isAfter(validUntil));
    }
}
