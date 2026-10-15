package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectBaselineDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.ProjectBaselineDtos.MilestoneSnapshot;
import cvt.cv.ppmbackend.dto.ProjectBaselineDtos.PhaseSnapshot;
import cvt.cv.ppmbackend.dto.ProjectBaselineDtos.Response;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectBaselineRepository;
import cvt.cv.ppmbackend.repository.ProjectMilestoneRepository;
import cvt.cv.ppmbackend.repository.ProjectPhaseRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProjectBaselineService {
    private final ProjectBaselineRepository baselines;
    private final ProjectPhaseRepository phases;
    private final ProjectMilestoneRepository milestones;
    private final ProjectRepository projects;
    private final ProjectGuard guard;
    private final ProjectEventService events;

    public ProjectBaselineService(ProjectBaselineRepository baselines, ProjectPhaseRepository phases,
            ProjectMilestoneRepository milestones, ProjectRepository projects, ProjectGuard guard,
            ProjectEventService events) {
        this.baselines = baselines;
        this.phases = phases;
        this.milestones = milestones;
        this.projects = projects;
        this.guard = guard;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID projectId) {
        Project project = guard.require(projectId);
        if (!guard.canView(project)) throw new ResourceNotFoundException("Projeto não encontrado: " + projectId);
        return baselines.findByProjectIdOrderByVersionDesc(projectId).stream()
                .map(ProjectBaselineService::toResponse).toList();
    }

    public Response create(UUID projectId, CreateRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        String reason = trim(request == null ? null : request.reason());
        if (reason == null) throw new BadRequestException("reason é obrigatória para criar uma linha de base.");
        return toResponse(capture(project, reason, actor));
    }

    public void captureIfAbsent(Project project, String reason, String actor) {
        if (!baselines.existsByProjectId(project.getId())) capture(project, reason, actor);
    }

    public ProjectBaseline captureVersion(Project project, String reason, String actor) {
        String value = trim(reason);
        if (value == null) throw new BadRequestException("reason é obrigatória para criar uma linha de base.");
        return capture(project, value, actor);
    }

    private ProjectBaseline capture(Project project, String reason, String actor) {
        int version = baselines.findFirstByProjectIdOrderByVersionDesc(project.getId())
                .map(b -> b.getVersion() + 1).orElse(1);
        ProjectBaseline baseline = new ProjectBaseline();
        baseline.setProject(project);
        baseline.setVersion(version);
        baseline.setCapturedAt(Instant.now());
        baseline.setCapturedBy(actor(actor));
        baseline.setReason(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
        baseline.setBudget(project.getBudget());
        if (project.getExecution() != null) {
            baseline.setPlannedStartDate(project.getExecution().getPlannedStartDate());
            baseline.setPlannedEndDate(project.getExecution().getPlannedEndDate());
        }
        for (ProjectPhase phase : phases.findByProjectIdOrderBySequenceAsc(project.getId())) {
            ProjectBaselinePhase snapshot = new ProjectBaselinePhase();
            snapshot.setBaseline(baseline);
            snapshot.setPhaseId(phase.getId());
            snapshot.setName(phase.getName());
            snapshot.setSequence(phase.getSequence());
            snapshot.setWeight(phase.getWeight());
            snapshot.setPlannedStartDate(phase.getPlannedStartDate());
            snapshot.setPlannedEndDate(phase.getPlannedEndDate());
            baseline.getPhases().add(snapshot);
        }
        for (ProjectMilestone milestone : milestones.findByProjectIdOrderByDueDateAscCreatedAtAsc(project.getId())) {
            ProjectBaselineMilestone snapshot = new ProjectBaselineMilestone();
            snapshot.setBaseline(baseline);
            snapshot.setMilestoneId(milestone.getId());
            snapshot.setName(milestone.getName());
            snapshot.setDueDate(milestone.getDueDate());
            baseline.getMilestones().add(snapshot);
        }
        ProjectBaseline saved = baselines.save(baseline);
        project.setBaselineBudget(saved.getBudget());
        project.setBaselineEndDate(saved.getPlannedEndDate());
        projects.save(project);
        events.log(project, ProjectEventType.BASELINE_CREATED,
                "Linha de base v" + version + " criada: " + saved.getReason(), actor);
        return saved;
    }

    private static Response toResponse(ProjectBaseline b) {
        return new Response(b.getId(), b.getProject().getId(), b.getVersion(), b.getCapturedAt(), b.getCapturedBy(),
                b.getReason(), b.getBudget(), b.getPlannedStartDate(), b.getPlannedEndDate(),
                b.getPhases().stream().map(p -> new PhaseSnapshot(p.getPhaseId(), p.getName(), p.getSequence(),
                        p.getPlannedStartDate(), p.getPlannedEndDate(), p.getWeight())).toList(),
                b.getMilestones().stream().map(m -> new MilestoneSnapshot(m.getMilestoneId(), m.getName(),
                        m.getDueDate())).toList());
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String actor(String value) { return value == null || value.isBlank() ? "system" : value.trim(); }
}
