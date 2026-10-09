package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cvt.cv.ppmbackend.enums.ChangeRequestKind;
import cvt.cv.ppmbackend.enums.ChangeRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Pedido de alteração de orçamento, prazo ou âmbito. {@code currentBudget}/{@code currentEndDate} são
 * copiados do projeto no momento do pedido e ficam como linha de base.
 */
@Entity
@Table(name = "project_change_request",
        indexes = @Index(name = "idx_project_change_request_project_id", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectChangeRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChangeRequestKind kind;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(name = "current_budget", precision = 18, scale = 2)
    private BigDecimal currentBudget;

    @Column(name = "current_end_date")
    private LocalDate currentEndDate;

    @Column(name = "proposed_budget", precision = 18, scale = 2)
    private BigDecimal proposedBudget;

    @Column(name = "proposed_end_date")
    private LocalDate proposedEndDate;

    @Column(name = "scope_change", columnDefinition = "TEXT")
    private String scopeChange;

    @Column(columnDefinition = "TEXT")
    private String impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status = ChangeRequestStatus.PENDING;

    @Column(name = "requested_by", nullable = false, length = 150)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;
}
