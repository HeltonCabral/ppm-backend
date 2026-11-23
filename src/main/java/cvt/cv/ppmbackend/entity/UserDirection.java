package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.UserDirectionSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Direção a que um utilizador pertence, para efeitos de acesso às demandas. Um utilizador pode ter
 * várias direções; a atribuição deixa de contar depois de {@code validUntil}.
 */
@Entity
@Table(name = "user_direction",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_direction_user_direction",
                columnNames = { "user_name", "direction_code" }),
        indexes = @Index(name = "idx_user_direction_user_name", columnList = "user_name"))
@Getter
@Setter
@NoArgsConstructor
public class UserDirection extends BaseEntity {

    /** Utilizador do AD, sempre em minúsculas. */
    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "full_name", length = 200)
    private String fullName;

    /** Código da direção no hub, em maiúsculas. */
    @Column(name = "direction_code", nullable = false, length = 20)
    private String directionCode;

    @Column(name = "direction_name", length = 200)
    private String directionName;

    /** Último dia em que a atribuição conta; {@code null} = sem fim. */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserDirectionSource source = UserDirectionSource.MANUAL;

    @Column(name = "ad_department", length = 300)
    private String adDepartment;

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
