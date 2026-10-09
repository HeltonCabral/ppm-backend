package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.MilestoneRequest;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.MilestoneResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectMilestone;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.enums.MilestoneStatus;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectMilestoneRepository;
import cvt.cv.ppmbackend.repository.ProjectPhaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static cvt.cv.ppmbackend.service.ProjectEventService.date;

/** Marcos e entregas do projeto. */
@Service
@Transactional
public class ProjectMilestoneService {
    private final ProjectMilestoneRepository milestones;
    private final ProjectPhaseRepository phases;
    private final ProjectGuard guard;
    private final ProjectEventService events;

    public ProjectMilestoneService(ProjectMilestoneRepository milestones, ProjectPhaseRepository phases,
            ProjectGuard guard, ProjectEventService events) {
        this.milestones = milestones;
        this.phases = phases;
        this.guard = guard;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(UUID projectId) {
        guard.require(projectId);
        return milestones.findByProjectIdOrderByDueDateAscCreatedAtAsc(projectId).stream()
                .map(ProjectMilestoneService::toResponse).toList();
    }

    public MilestoneResponse create(UUID projectId, MilestoneRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProject(project);
        milestone.setCreatedBy(actor(actor));
        apply(milestone, projectId, request, actor);
        milestones.save(milestone);
        events.log(project, ProjectEventType.MILESTONE_ADDED, "Marco " + milestone.getName() + " acrescentado"
                + (milestone.getDueDate() == null ? "" : " (prazo " + date(milestone.getDueDate()) + ")"), actor);
        if (milestone.getStatus() == MilestoneStatus.DONE) {
            events.log(project, ProjectEventType.MILESTONE_DONE, "Marco " + milestone.getName() + " concluído", actor);
        }
        return toResponse(milestone);
    }

    public MilestoneResponse update(UUID projectId, UUID milestoneId, MilestoneRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectMilestone milestone = require(projectId, milestoneId);
        MilestoneStatus previous = milestone.getStatus();
        apply(milestone, projectId, request, actor);
        milestones.save(milestone);
        boolean nowDone = milestone.getStatus() == MilestoneStatus.DONE && previous != MilestoneStatus.DONE;
        events.log(project, nowDone ? ProjectEventType.MILESTONE_DONE : ProjectEventType.MILESTONE_UPDATED,
                "Marco " + milestone.getName() + (nowDone ? " concluído" : " alterado"), actor);
        return toResponse(milestone);
    }

    public void delete(UUID projectId, UUID milestoneId, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectMilestone milestone = require(projectId, milestoneId);
        milestones.delete(milestone);
        events.log(project, ProjectEventType.MILESTONE_REMOVED, "Marco " + milestone.getName() + " removido", actor);
    }

    private void apply(ProjectMilestone milestone, UUID projectId, MilestoneRequest request, String actor) {
        if (request == null) {
            throw new BadRequestException("Dados do marco em falta.");
        }
        String name = trim(request.name());
        if (name == null) {
            throw new BadRequestException("O nome do marco é obrigatório.");
        }
        if (name.length() > 180) {
            throw new BadRequestException("O nome do marco não pode exceder 180 caracteres.");
        }
        String owner = trim(request.owner());
        if (owner != null && owner.length() > 150) {
            throw new BadRequestException("O responsável não pode exceder 150 caracteres.");
        }
        ProjectPhase phase = null;
        if (request.phaseId() != null) {
            phase = phases.findByIdAndProjectId(request.phaseId(), projectId)
                    .orElseThrow(() -> new BadRequestException("A fase indicada não pertence a este projeto."));
        }
        MilestoneStatus status = request.status() == null ? MilestoneStatus.PENDING : request.status();
        milestone.setName(name);
        milestone.setDescription(trim(request.description()));
        milestone.setPhase(phase);
        milestone.setDueDate(request.dueDate());
        milestone.setStatus(status);
        milestone.setCompletedAt(status == MilestoneStatus.DONE
                ? (request.completedAt() != null ? request.completedAt() : LocalDate.now())
                : null);
        milestone.setDeliverable(Boolean.TRUE.equals(request.deliverable()));
        milestone.setOwner(owner);
        milestone.setUpdatedBy(actor(actor));
    }

    private ProjectMilestone require(UUID projectId, UUID milestoneId) {
        return milestones.findByIdAndProjectId(milestoneId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Marco não encontrado neste projeto: " + milestoneId));
    }

    static MilestoneResponse toResponse(ProjectMilestone m) {
        boolean late = m.getStatus() == MilestoneStatus.PENDING && m.getDueDate() != null
                && m.getDueDate().isBefore(LocalDate.now());
        return new MilestoneResponse(m.getId(), m.getName(), m.getDescription(),
                m.getPhase() == null ? null : m.getPhase().getId(), m.getPhase() == null ? null : m.getPhase().getName(),
                m.getDueDate(), m.getCompletedAt(), m.getStatus(), m.isDeliverable(), m.getOwner(), late,
                m.getCreatedBy(), m.getCreatedAt(), m.getUpdatedBy(), m.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
