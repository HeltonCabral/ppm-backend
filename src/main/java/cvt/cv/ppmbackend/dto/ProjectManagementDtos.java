package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.BenefitType;
import cvt.cv.ppmbackend.enums.ChangeRequestKind;
import cvt.cv.ppmbackend.enums.ChangeRequestStatus;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.MilestoneStatus;
import cvt.cv.ppmbackend.enums.ObjectivesMet;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.enums.ProjectPhaseStatus;
import cvt.cv.ppmbackend.enums.ProjectRiskKind;
import cvt.cv.ppmbackend.enums.ProjectRiskStatus;
import cvt.cv.ppmbackend.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos da gestão de projetos: fases, marcos, relatórios, riscos, alterações, encerramento e histórico. */
public final class ProjectManagementDtos {
    private ProjectManagementDtos() {
    }

    // ----- Histórico -----

    public record EventResponse(UUID id, Instant at, String actor, ProjectEventType type, String description) {
    }

    // ----- Fases -----

    public record PhaseResponse(UUID id, String name, Integer sequence, BigDecimal weight,
            LocalDate plannedStartDate, LocalDate plannedEndDate, LocalDate actualStartDate, LocalDate actualEndDate,
            ProjectPhaseStatus status, Integer progress, String updatedBy, Instant updatedAt) {
    }

    public record PhaseUpdateRequest(ProjectPhaseStatus status, Integer progress, LocalDate plannedStartDate,
            LocalDate plannedEndDate, LocalDate actualStartDate, LocalDate actualEndDate) {
    }

    // ----- Marcos -----

    public record MilestoneRequest(String name, String description, UUID phaseId, LocalDate dueDate,
            MilestoneStatus status, LocalDate completedAt, Boolean deliverable, String owner) {
    }

    /** {@code late}: PENDING com {@code dueDate} anterior a hoje. */
    public record MilestoneResponse(UUID id, String name, String description, UUID phaseId, String phaseName,
            LocalDate dueDate, LocalDate completedAt, MilestoneStatus status, boolean deliverable, String owner,
            boolean late, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }

    // ----- Relatórios de estado -----

    public record StatusReportRequest(LocalDate periodEnd, ExecutiveStatus overallStatus,
            ExecutiveStatus scheduleStatus, ExecutiveStatus costStatus, ExecutiveStatus scopeStatus,
            ExecutiveStatus riskStatus, Integer progress, BigDecimal consumedBudget, String summary,
            String achievements, String nextSteps, String blockers, Boolean needsSupport) {
    }

    public record StatusReportResponse(UUID id, UUID projectId, LocalDate periodEnd, ExecutiveStatus overallStatus,
            ExecutiveStatus scheduleStatus, ExecutiveStatus costStatus, ExecutiveStatus scopeStatus,
            ExecutiveStatus riskStatus, Integer progress, BigDecimal consumedBudget, String summary,
            String achievements, String nextSteps, String blockers, boolean needsSupport, String createdBy,
            Instant createdAt) {
    }

    // ----- Riscos e problemas -----

    public record RiskRequest(ProjectRiskKind kind, String title, String description, Integer probability,
            Integer impact, ProjectRiskStatus status, String response, String owner, LocalDate dueDate) {
    }

    /** {@code score} = probabilidade × impacto; {@code level} 1–4 LOW, 5–9 MEDIUM, 10–15 HIGH, 16–25 CRITICAL. */
    public record RiskResponse(UUID id, UUID projectId, String projectCode, String projectName, ProjectRiskKind kind,
            String title, String description, Integer probability, Integer impact, int score, RiskLevel level,
            ProjectRiskStatus status, String response, String owner, LocalDate dueDate, String createdBy,
            Instant createdAt, String updatedBy, Instant updatedAt) {
    }

    // ----- Pedidos de alteração -----

    public record ChangeRequestCreate(ChangeRequestKind kind, String title, String justification,
            BigDecimal proposedBudget, LocalDate proposedEndDate, String scopeChange, String impact) {
    }

    public record DecisionRequest(String note) {
    }

    public record ChangeRequestResponse(UUID id, UUID projectId, ChangeRequestKind kind, String title,
            String justification, BigDecimal currentBudget, LocalDate currentEndDate, BigDecimal proposedBudget,
            LocalDate proposedEndDate, String scopeChange, String impact, ChangeRequestStatus status,
            String requestedBy, Instant requestedAt, String decidedBy, Instant decidedAt, String decisionNote) {
    }

    // ----- Encerramento -----

    /** Corpo opcional de {@code POST /projects/{id}/complete}. */
    public record CompleteRequest(ClosureRequest closure) {
    }

    public record ClosureRequest(String summary, ObjectivesMet objectivesMet, List<String> lessonsLearned,
            String handover) {
    }

    public record ClosureResponse(UUID projectId, String summary, ObjectivesMet objectivesMet, String handover,
            List<String> lessonsLearned, String createdBy, Instant createdAt) {
    }

    public record LessonLearnedResponse(UUID id, UUID projectId, String projectCode, String projectName,
            LocalDate date, String description, Integer sequence, String createdBy) {
    }

    // ----- Benefícios -----

    public record BenefitRequest(UUID projectId, BenefitType benefitType, BigDecimal expectedValue,
            BigDecimal realizedValue, LocalDate trackingDate, String notes) {
    }

    public record BenefitResponse(UUID id, UUID projectId, String projectCode, String projectName,
            BenefitType benefitType, BigDecimal expectedValue, BigDecimal realizedValue, LocalDate trackingDate,
            String notes, String createdBy, Instant createdAt) {
    }
}
