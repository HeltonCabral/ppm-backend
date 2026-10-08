package cvt.cv.ppmbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Tradução de um departamento do AD para uma direção do hub. Só serve para o frontend sugerir a
 * direção de um utilizador; não decide acessos.
 */
@Entity
@Table(name = "department_direction_mapping",
        uniqueConstraints = @UniqueConstraint(name = "uk_department_direction_mapping_key",
                columnNames = "department_key"))
@Getter
@Setter
@NoArgsConstructor
public class DepartmentDirectionMapping extends BaseEntity {

    /** Chave normalizada: maiúsculas, sem acentos ({@code CRN}, {@code TRANSMISSAO}). */
    @Column(name = "department_key", nullable = false, length = 100)
    private String departmentKey;

    @Column(name = "direction_code", nullable = false, length = 20)
    private String directionCode;

    @Column(name = "direction_name", length = 200)
    private String directionName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy = "system";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 150)
    private String updatedBy = "system";
}
