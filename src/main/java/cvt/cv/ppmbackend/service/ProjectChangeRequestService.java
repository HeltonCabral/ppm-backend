package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.ChangeRequestCreate;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.ChangeRequestResponse;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.DecisionRequest;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectChangeRequest;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.enums.ChangeRequestKind;
import cvt.cv.ppmbackend.enums.ChangeRequestStatus;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectChangeRequestRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cvt.cv.ppmbackend.service.ProjectEventService.date;
import static cvt.cv.ppmbackend.service.ProjectEventService.money;

/**
 * Pedidos de alteração de orçamento, prazo ou âmbito. O valor atual é copiado do projeto no pedido (linha
 * de base); a aprovação aplica a alteração na mesma transação.
 */
@Service
@Transactional
public class ProjectChangeRequestService {
    private final ProjectChangeRequestRepository requests;
    private final ProjectRepository projects;
    private final ProjectGuard guard;
    private final ProjectPhaseService phases;
    private final ProjectEventService events;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectBaselineService baselines;

    public ProjectChangeRequestService(ProjectChangeRequestRepository requests, ProjectRepository projects,
            ProjectGuard guard, ProjectPhaseService phases, ProjectEventService events) {
        this.requests = requests;
        this.projects = projects;
        this.guard = guard;
        this.phases = phases;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> list(UUID projectId) {
        guard.require(projectId);
        return requests.findByProjectIdOrderByRequestedAtDesc(projectId).stream()
                .map(ProjectChangeRequestService::toResponse).toList();
    }

    public ChangeRequestResponse create(UUID projectId, ChangeRequestCreate r, String actor) {
        Project project = guard.requireWritable(projectId);
        if (r == null || r.kind() == null) {
            throw new BadRequestException("kind é obrigatório (BUDGET, SCHEDULE ou SCOPE).");
        }
        String title = trim(r.title());
        String justification = trim(r.justification());
        if (title == null) {
            throw new BadRequestException("O título é obrigatório.");
        }
        if (title.length() > 180) {
            throw new BadRequestException("O título não pode exceder 180 caracteres.");
        }
        if (justification == null) {
            throw new BadRequestException("A justificação é obrigatória.");
        }
        BigDecimal currentBudget = project.getBudget();
        LocalDate currentEnd = project.getExecution() == null ? null : project.getExecution().getPlannedEndDate();
        ProjectChangeRequest request = new ProjectChangeRequest();
        switch (r.kind()) {
            case BUDGET -> {
                if (r.proposedBudget() == null || r.proposedBudget().signum() <= 0) {
                    throw new BadRequestException("O orçamento proposto é obrigatório e tem de ser maior que zero.");
                }
                if (currentBudget != null && r.proposedBudget().compareTo(currentBudget) == 0) {
                    throw new BadRequestException("O orçamento proposto é igual ao atual.");
                }
                request.setProposedBudget(r.proposedBudget());
            }
            case SCHEDULE -> {
                if (r.proposedEndDate() == null) {
                    throw new BadRequestException("A nova data de fim é obrigatória.");
                }
                if (r.proposedEndDate().equals(currentEnd)) {
                    throw new BadRequestException("A nova data de fim é igual à atual.");
                }
                LocalDate start = project.getExecution() == null ? null : project.getExecution().getPlannedStartDate();
                if (start != null && r.proposedEndDate().isBefore(start)) {
                    throw new BadRequestException("A nova data de fim não pode ser anterior ao início previsto ("
                            + date(start) + ").");
                }
                request.setProposedEndDate(r.proposedEndDate());
            }
            case SCOPE -> {
                String scope = trim(r.scopeChange());
                if (scope == null) {
                    throw new BadRequestException("Descreva a alteração de âmbito.");
                }
                request.setScopeChange(scope);
            }
        }
        if (requests.existsByProjectIdAndKindAndStatus(projectId, r.kind(), ChangeRequestStatus.PENDING)) {
            throw new DomainException(HttpStatus.CONFLICT, "CHANGE_REQUEST_PENDING",
                    "Já existe um pedido de alteração de " + kindLabel(r.kind()) + " pendente.",
                    Map.of("kind", r.kind().name()));
        }
        request.setProject(project);
        request.setKind(r.kind());
        request.setTitle(title);
        request.setJustification(justification);
        request.setImpact(trim(r.impact()));
        request.setCurrentBudget(currentBudget);
        request.setCurrentEndDate(currentEnd);
        request.setStatus(ChangeRequestStatus.PENDING);
        request.setRequestedBy(actor(actor));
        request.setRequestedAt(Instant.now());
        requests.save(request);
        events.log(project, ProjectEventType.CHANGE_REQUESTED,
                "Pedido de alteração de " + kindLabel(r.kind()) + ": " + change(request), actor);
        return toResponse(request);
    }

    public ChangeRequestResponse approve(UUID projectId, UUID requestId, DecisionRequest decision, String actor) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode aprovar pedidos de alteração.");
        Project project = guard.requireWritable(projectId);
        ProjectChangeRequest request = requirePending(projectId, requestId);
        switch (request.getKind()) {
            case BUDGET -> {
                project.setBudget(request.getProposedBudget());
                events.log(project, ProjectEventType.BUDGET_CHANGED, "Orçamento alterado: "
                        + money(request.getCurrentBudget()) + " → " + money(request.getProposedBudget()), actor);
            }
            case SCHEDULE -> {
                ProjectExecution execution = project.getExecution();
                if (execution == null) {
                    execution = new ProjectExecution();
                    project.attachExecution(execution);
                }
                execution.setPlannedEndDate(request.getProposedEndDate());
                execution.setLastUpdatedAt(Instant.now());
                execution.setLastUpdatedBy(actor(actor));
                phases.redistribute(project, request.getProposedEndDate());
            }
            case SCOPE -> { }
        }
        decide(request, ChangeRequestStatus.APPROVED, decision, actor);
        project.setUpdatedBy(actor(actor));
        projects.save(project);
        if (request.getKind() == ChangeRequestKind.SCHEDULE && baselines != null) {
            baselines.captureVersion(project, request.getTitle(), actor);
        }
        events.log(project, ProjectEventType.CHANGE_APPROVED, "Pedido de alteração de " + kindLabel(request.getKind())
                + " aprovado: " + change(request), actor);
        return toResponse(request);
    }

    public ChangeRequestResponse reject(UUID projectId, UUID requestId, DecisionRequest decision, String actor) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode rejeitar pedidos de alteração.");
        if (decision == null || trim(decision.note()) == null) {
            throw new BadRequestException("Indique o motivo da rejeição.");
        }
        Project project = guard.requireWritable(projectId);
        ProjectChangeRequest request = requirePending(projectId, requestId);
        decide(request, ChangeRequestStatus.REJECTED, decision, actor);
        events.log(project, ProjectEventType.CHANGE_REJECTED, "Pedido de alteração de " + kindLabel(request.getKind())
                + " rejeitado: " + trim(decision.note()), actor);
        return toResponse(request);
    }

    /** Só o autor, ou {@code projeto_editar_todos}, quando o filtro de projetos está ligado. */
    public ChangeRequestResponse cancel(UUID projectId, UUID requestId, DecisionRequest decision, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectChangeRequest request = requirePending(projectId, requestId);
        if (guard.filterEnabled()) {
            UserAccess user = guard.currentUser();
            boolean author = request.getRequestedBy() != null && request.getRequestedBy().equalsIgnoreCase(user.userName());
            if (!author && !user.has(UserAccess.PROJECT_EDIT_ALL)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "PROJECT_EDIT_FORBIDDEN",
                        "Só o autor do pedido pode cancelá-lo.", Map.of());
            }
        }
        decide(request, ChangeRequestStatus.CANCELLED, decision, actor);
        events.log(project, ProjectEventType.CHANGE_CANCELLED, "Pedido de alteração de "
                + kindLabel(request.getKind()) + " cancelado", actor);
        return toResponse(request);
    }

    private void decide(ProjectChangeRequest request, ChangeRequestStatus status, DecisionRequest decision,
            String actor) {
        request.setStatus(status);
        request.setDecidedBy(actor(actor));
        request.setDecidedAt(Instant.now());
        request.setDecisionNote(decision == null ? null : trim(decision.note()));
        requests.save(request);
    }

    private ProjectChangeRequest requirePending(UUID projectId, UUID requestId) {
        ProjectChangeRequest request = requests.findByIdAndProjectId(requestId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pedido de alteração não encontrado neste projeto: " + requestId));
        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            throw new DomainException(HttpStatus.CONFLICT, "CHANGE_REQUEST_NOT_PENDING",
                    "Este pedido já não está pendente.", Map.of("status", request.getStatus().name()));
        }
        return request;
    }

    private static String change(ProjectChangeRequest r) {
        return switch (r.getKind()) {
            case BUDGET -> money(r.getCurrentBudget()) + " → " + money(r.getProposedBudget());
            case SCHEDULE -> date(r.getCurrentEndDate()) + " → " + date(r.getProposedEndDate());
            case SCOPE -> Objects.requireNonNullElse(r.getTitle(), "");
        };
    }

    static String kindLabel(ChangeRequestKind kind) {
        return switch (kind) {
            case BUDGET -> "orçamento";
            case SCHEDULE -> "prazo";
            case SCOPE -> "âmbito";
        };
    }

    static ChangeRequestResponse toResponse(ProjectChangeRequest r) {
        return new ChangeRequestResponse(r.getId(), r.getProject().getId(), r.getKind(), r.getTitle(),
                r.getJustification(), r.getCurrentBudget(), r.getCurrentEndDate(), r.getProposedBudget(),
                r.getProposedEndDate(), r.getScopeChange(), r.getImpact(), r.getStatus(), r.getRequestedBy(),
                r.getRequestedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
