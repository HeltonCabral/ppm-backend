package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Área envolvida num projeto, herdada da Demanda de origem. As áreas da direção do projeto mantêm o
 * papel que tinham na Demanda; as das outras direções entram como intervenientes. {@code areaCode} nulo
 * representa uma direção interveniente sem área indicada.
 */
@Entity
@Table(name = "project_areas", indexes = @Index(name = "idx_project_areas_project_id", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectArea extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(name = "direction_code", length = 60)
    private String directionCode;

    @Column(name = "direction_name", length = 120)
    private String directionName;

    @Column(name = "area_code", length = 60)
    private String areaCode;

    @Column(name = "area_name", length = 120)
    private String areaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AreaRole role;

    /** Como a direção participava na Demanda (implementação ou interveniente). */
    @Enumerated(EnumType.STRING)
    @Column(name = "participation_type", length = 50)
    private DirectionParticipationType participationType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
