package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.MemberType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project_team_members")
@Getter
@Setter
@NoArgsConstructor
public class ProjectTeamMember extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(length = 50)
    private String code;

    @Column(name = "direction_name", length = 200)
    private String directionName;

    @Column(name = "direction_code", length = 100)
    private String directionCode;

    @Column(name = "area_name", length = 200)
    private String areaName;

    @Column(name = "area_code", length = 100)
    private String areaCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 100)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberType type;

    /** Perfil do projeto que este membro preenche; nulo para membros sem perfil previsto. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_profile_id")
    @JsonIgnore
    private ProjectProfile projectProfile;

    /** Parte (1–100) de uma pessoa a tempo inteiro dedicada ao projeto. Os registos antigos ficam com 100. */
    @Column(name = "allocation_percentage")
    @org.hibernate.annotations.ColumnDefault("100")
    private Integer allocationPercentage = 100;

    /** Início e fim da alocação; nulos = período efetivo (fases do perfil, senão datas do projeto). */
    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
