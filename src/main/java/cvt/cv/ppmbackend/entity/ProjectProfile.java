package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ProjectProfileOrigin;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Perfil previsto para o projeto, herdado dos recursos da Demanda de origem. Os membros da equipa
 * ({@link ProjectTeamMember}) são associados a um destes perfis.
 *
 * <p>{@code requiredQuantity} é o n.º de pessoas do perfil em simultâneo: o máximo por fase, não a soma
 * das fases (Analista ×1 em Desenvolvimento e ×1 em Testes = 1 pessoa).
 */
@Entity
@Table(name = "project_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_profiles_project_profile",
                columnNames = { "project_id", "profile_id" }),
        indexes = @Index(name = "idx_project_profiles_project_id", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    /** Nome do perfil no momento da herança, para o histórico não mudar se o perfil for renomeado. */
    @Column(name = "profile_name", nullable = false, length = 150)
    private String profileName;

    @Column(name = "direction_code", length = 60)
    private String directionCode;

    @Column(name = "direction_name", length = 120)
    private String directionName;

    @Column(name = "required_quantity", nullable = false)
    private Integer requiredQuantity;

    /** Fases em que o perfil é necessário, separadas por "; ", pela ordem indicada. Vazio = todo o projeto. */
    @Column(name = "phase_names", length = 1000)
    private String phaseNames;

    /** {@code DEMAND} nos registos anteriores a esta coluna (valor por defeito da coluna). */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @ColumnDefault("'DEMAND'")
    private ProjectProfileOrigin origin = ProjectProfileOrigin.DEMAND;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;
}
