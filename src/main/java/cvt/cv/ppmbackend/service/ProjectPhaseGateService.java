package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectPhaseGateDtos.*;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectMilestone;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.entity.ProjectPhaseGate;
import cvt.cv.ppmbackend.entity.ProjectPhaseGateItem;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectMilestoneRepository;
import cvt.cv.ppmbackend.repository.ProjectPhaseGateRepository;
import cvt.cv.ppmbackend.repository.ProjectPhaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ProjectPhaseGateService {
    private final ProjectPhaseGateRepository gates;
    private final ProjectPhaseRepository phases;
    private final ProjectMilestoneRepository milestones;
    private final ProjectGuard guard;
    private final ProjectPhaseService phaseService;
    private final ProjectEventService events;

    public ProjectPhaseGateService(ProjectPhaseGateRepository gates, ProjectPhaseRepository phases,
            ProjectMilestoneRepository milestones, ProjectGuard guard, ProjectPhaseService phaseService,
            ProjectEventService events) {
        this.gates = gates;
        this.phases = phases;
        this.milestones = milestones;
        this.guard = guard;
        this.phaseService = phaseService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID projectId) {
        Project project = guard.require(projectId);
        if (!guard.canView(project)) throw new ResourceNotFoundException("Projeto não encontrado: " + projectId);
        return gates.findByPhase_Project_IdOrderByPhase_SequenceAsc(projectId).stream().map(this::toResponse).toList();
    }

    public Response update(UUID projectId, UUID phaseId, UpdateRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectPhase phase = requirePhase(projectId, phaseId);
        ProjectPhaseGate gate = gate(phase);
        List<ChecklistItemRequest> items = request == null || request.checklist() == null
                ? List.of() : request.checklist();
        Set<UUID> existing = new HashSet<>();
        gate.getChecklist().forEach(i -> existing.add(i.getId()));
        for (ChecklistItemRequest item : items) {
            if (item.id() != null && !existing.contains(item.id())) {
                throw new BadRequestException("O item " + item.id() + " não pertence à passagem desta fase.");
            }
            if (trim(item.label()) == null) throw new BadRequestException("O texto do item é obrigatório.");
            if (item.label().trim().length() > 300) throw new BadRequestException("O item não pode exceder 300 caracteres.");
        }
        Map<UUID, ProjectPhaseGateItem> current = new HashMap<>();
        gate.getChecklist().forEach(i -> current.put(i.getId(), i));
        gate.getChecklist().clear();
        for (ChecklistItemRequest item : items) {
            ProjectPhaseGateItem entity = item.id() == null ? new ProjectPhaseGateItem() : current.get(item.id());
            entity.setGate(gate);
            entity.setLabel(item.label().trim());
            entity.setMet(Boolean.TRUE.equals(item.met()));
            gate.getChecklist().add(entity);
        }
        ProjectPhaseGate saved = gates.save(gate);
        events.log(project, ProjectEventType.GATE_UPDATED,
                "Lista de verificação da fase " + phase.getName() + " atualizada", actor);
        return toResponse(saved);
    }

    public Response pass(UUID projectId, UUID phaseId, PassRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        requireRunning(project);
        ProjectPhase phase = requirePhase(projectId, phaseId);
        if (phase.getStatus() != ProjectPhaseStatus.IN_PROGRESS) {
            throw new DomainException(HttpStatus.CONFLICT, "PHASE_NOT_IN_PROGRESS",
                    "A passagem só pode ser pedida para a fase em curso.",
                    Map.of("status", phase.getStatus().name()));
        }
        ProjectPhaseGate gate = gate(phase);
        boolean met = allMet(gate);
        String justification = trim(request == null ? null : request.justification());
        if (!met && justification == null) {
            throw new BadRequestException("Há critérios por cumprir; indique uma justificação.");
        }
        Instant now = Instant.now();
        if (met) {
            gate.setStatus(ProjectPhaseGateStatus.APPROVED);
            gate.setJustification(null);
            gate.setDecidedBy(actor(actor));
            gate.setDecidedAt(now);
            gate.setDecisionNote(null);
            phaseService.completeFromGate(project, phase, actor);
            events.log(project, ProjectEventType.GATE_APPROVED,
                    "Passagem da fase " + phase.getName() + " aprovada automaticamente", actor);
        } else {
            gate.setStatus(ProjectPhaseGateStatus.REQUESTED);
            gate.setJustification(justification);
            gate.setRequestedBy(actor(actor));
            gate.setRequestedAt(now);
            gate.setDecidedBy(null);
            gate.setDecidedAt(null);
            gate.setDecisionNote(null);
            events.log(project, ProjectEventType.GATE_REQUESTED,
                    "Passagem da fase " + phase.getName() + " pedida com critérios por cumprir", actor);
        }
        return toResponse(gates.save(gate));
    }

    public Response approve(UUID projectId, UUID phaseId, DecisionRequest request, String actor) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode aprovar passagens de fase.");
        Project project = guard.requireWritable(projectId);
        requireRunning(project);
        ProjectPhase phase = requirePhase(projectId, phaseId);
        ProjectPhaseGate gate = requireRequested(phaseId);
        gate.setStatus(ProjectPhaseGateStatus.APPROVED);
        gate.setDecidedBy(actor(actor));
        gate.setDecidedAt(Instant.now());
        gate.setDecisionNote(trim(request == null ? null : request.note()));
        phaseService.completeFromGate(project, phase, actor);
        gates.save(gate);
        events.log(project, ProjectEventType.GATE_APPROVED, "Passagem da fase " + phase.getName() + " aprovada", actor);
        return toResponse(gate);
    }

    public Response reject(UUID projectId, UUID phaseId, DecisionRequest request, String actor) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode rejeitar passagens de fase.");
        Project project = guard.requireWritable(projectId);
        ProjectPhase phase = requirePhase(projectId, phaseId);
        ProjectPhaseGate gate = requireRequested(phaseId);
        String note = trim(request == null ? null : request.note());
        if (note == null) throw new BadRequestException("A nota da rejeição é obrigatória.");
        gate.setStatus(ProjectPhaseGateStatus.REJECTED);
        gate.setDecidedBy(actor(actor));
        gate.setDecidedAt(Instant.now());
        gate.setDecisionNote(note);
        gates.save(gate);
        events.log(project, ProjectEventType.GATE_REJECTED, "Passagem da fase " + phase.getName() + " rejeitada", actor);
        return toResponse(gate);
    }

    private ProjectPhaseGate gate(ProjectPhase phase) {
        return gates.findByPhaseId(phase.getId()).orElseGet(() -> {
            ProjectPhaseGate gate = new ProjectPhaseGate();
            gate.setPhase(phase);
            return gate;
        });
    }

    private ProjectPhaseGate requireRequested(UUID phaseId) {
        ProjectPhaseGate gate = gates.findByPhaseId(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Não existe pedido de passagem para esta fase."));
        if (gate.getStatus() != ProjectPhaseGateStatus.REQUESTED) {
            throw new DomainException(HttpStatus.CONFLICT, "GATE_NOT_REQUESTED",
                    "A passagem de fase não está pendente.", Map.of("status", gate.getStatus().name()));
        }
        return gate;
    }

    private ProjectPhase requirePhase(UUID projectId, UUID phaseId) {
        return phases.findByIdAndProjectId(phaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Fase não encontrada neste projeto: " + phaseId));
    }

    private static void requireRunning(Project project) {
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new DomainException(HttpStatus.CONFLICT, "PROJECT_NOT_IN_PROGRESS",
                    "A passagem de fase só é possível com o projeto em curso.",
                    Map.of("status", project.getStatus().name()));
        }
    }

    private List<AutomaticCriterion> criteria(ProjectPhaseGate gate) {
        List<ProjectMilestone> rows = milestones.findByPhaseId(gate.getPhase().getId());
        boolean nonDeliverables = rows.stream().filter(m -> !m.isDeliverable())
                .allMatch(m -> m.getStatus() == MilestoneStatus.DONE || m.getStatus() == MilestoneStatus.CANCELLED);
        boolean deliverables = rows.stream().filter(ProjectMilestone::isDeliverable)
                .allMatch(m -> m.getStatus() == MilestoneStatus.DONE);
        return List.of(
                new AutomaticCriterion("MILESTONES", "Marcos da fase concluídos ou cancelados", nonDeliverables),
                new AutomaticCriterion("DELIVERABLES", "Entregáveis da fase concluídos", deliverables));
    }

    private boolean allMet(ProjectPhaseGate gate) {
        return criteria(gate).stream().allMatch(AutomaticCriterion::met)
                && gate.getChecklist().stream().allMatch(ProjectPhaseGateItem::isMet);
    }

    private Response toResponse(ProjectPhaseGate gate) {
        List<AutomaticCriterion> automatic = criteria(gate);
        boolean all = automatic.stream().allMatch(AutomaticCriterion::met)
                && gate.getChecklist().stream().allMatch(ProjectPhaseGateItem::isMet);
        ProjectPhase phase = gate.getPhase();
        return new Response(gate.getId(), phase.getProject().getId(), phase.getId(), phase.getName(), gate.getStatus(),
                gate.getJustification(), gate.getRequestedBy(), gate.getRequestedAt(), gate.getDecidedBy(),
                gate.getDecidedAt(), gate.getDecisionNote(), gate.getChecklist().stream()
                        .map(i -> new ChecklistItem(i.getId(), i.getLabel(), i.isMet())).toList(), automatic, all);
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String actor(String value) { return value == null || value.isBlank() ? "system" : value.trim(); }
}
