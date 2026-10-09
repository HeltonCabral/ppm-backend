package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Evento do histórico do projeto, com a frase pronta a mostrar. */
@Entity
@Table(name = "project_event", indexes = @Index(name = "idx_project_event_project_at", columnList = "project_id, at"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false, length = 150)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectEventType type;

    @Column(nullable = false, length = 1000)
    private String description;
}
