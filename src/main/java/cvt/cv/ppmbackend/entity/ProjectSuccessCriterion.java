package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Critério de sucesso do projeto, herdado da Demanda de origem. */
@Entity
@Table(name = "project_success_criteria",
        indexes = @Index(name = "idx_project_success_criteria_project_id", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectSuccessCriterion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
