package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhaseResponse;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhaseUpdateRequest;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.enums.ProjectPhaseStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectPhaseRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano por fases do projeto: criação a partir do planeamento da Demanda (ou de um Tipo), avanço de cada
 * fase, progresso do projeto pelo peso, arranque, encerramento e redistribuição após mudança de prazo.
 */
@Service
@Transactional
public class ProjectPhaseService {
    private static final Logger log = LoggerFactory.getLogger(ProjectPhaseService.class);

    private final ProjectPhaseRepository phases;
    private final ProjectRepository projects;
    private final ProjectGuard guard;
    private final ProjectEventService events;

    public ProjectPhaseService(ProjectPhaseRepository phases, ProjectRepository projects, ProjectGuard guard,
            ProjectEventService events) {
        this.phases = phases;
        this.projects = projects;
        this.guard = guard;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PhaseResponse> list(UUID projectId) {
        guard.require(projectId);
        return phases.findByProjectIdOrderBySequenceAsc(projectId).stream().map(ProjectPhaseService::toResponse).toList();
    }

    public PhaseResponse update(UUID projectId, UUID phaseId, PhaseUpdateRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectPhase phase = phases.findByIdAndProjectId(phaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Fase não encontrada neste projeto: " + phaseId));
        if (request == null) {
            throw new BadRequestException("Dados da fase em falta.");
        }
        if (request.progress() != null && (request.progress() < 0 || request.progress() > 100)) {
            throw new BadRequestException("progress tem de estar entre 0 e 100.");
        }
        ProjectPhaseStatus previousStatus = phase.getStatus();
        ProjectPhaseStatus status = request.status() != null ? request.status() : phase.getStatus();
        LocalDate today = LocalDate.now();
        LocalDate plannedStart = request.plannedStartDate() != null ? request.plannedStartDate() : phase.getPlannedStartDate();
        LocalDate plannedEnd = request.plannedEndDate() != null ? request.plannedEndDate() : phase.getPlannedEndDate();
        LocalDate actualStart = request.actualStartDate() != null ? request.actualStartDate() : phase.getActualStartDate();
        LocalDate actualEnd = request.actualEndDate() != null ? request.actualEndDate() : phase.getActualEndDate();
        int progress = request.progress() != null ? request.progress() : nz(phase.getProgress());

        switch (status) {
            case DONE -> {
                progress = 100;
                if (actualEnd == null) actualEnd = today;
                if (actualStart == null) actualStart = actualEnd;
            }
            case IN_PROGRESS -> {
                if (actualStart == null) actualStart = today;
                actualEnd = null;
            }
            case NOT_STARTED -> {
                progress = 0;
                actualStart = null;
                actualEnd = null;
            }
            case SKIPPED -> { }
        }
        if (plannedStart != null && plannedEnd != null && plannedEnd.isBefore(plannedStart)) {
            throw new BadRequestException("plannedEndDate não pode ser anterior a plannedStartDate.");
        }
        if (actualStart != null && actualEnd != null && actualEnd.isBefore(actualStart)) {
            throw new BadRequestException("actualEndDate não pode ser anterior a actualStartDate.");
        }

        phase.setStatus(status);
        phase.setProgress(progress);
        phase.setPlannedStartDate(plannedStart);
        phase.setPlannedEndDate(plannedEnd);
        phase.setActualStartDate(actualStart);
        phase.setActualEndDate(actualEnd);
        phase.setUpdatedBy(actor(actor));
        phases.save(phase);
        Integer projectProgress = recalculate(project, actor);

        String description = "Fase " + phase.getName()
                + (previousStatus != status ? ": " + label(previousStatus) + " → " + label(status) : " atualizada")
                + " (" + progress + "%)"
                + (projectProgress == null ? "" : "; projeto a " + projectProgress + "%");
        events.log(project, ProjectEventType.PHASE_UPDATED, description, actor);
        return toResponse(phase);
    }

    /** Cria as fases de um projeto novo (sem gravar: entram pela cascata do projeto). */
    public void createPhases(Project project, List<ProjectPhasePlanner.PhaseSeed> seeds, String actor) {
        if (!project.getPhases().isEmpty() || seeds.isEmpty()) {
            return;
        }
        project.getPhases().addAll(ProjectPhasePlanner.build(project, seeds, actor(actor)));
    }

    /** Arranque: a primeira fase (não SKIPPED) passa a IN_PROGRESS, com início real hoje. */
    public void startFirstPhase(Project project, String actor) {
        project.getPhases().stream()
                .filter(p -> p.getStatus() == ProjectPhaseStatus.NOT_STARTED)
                .min((a, b) -> a.getSequence().compareTo(b.getSequence()))
                .ifPresent(first -> {
                    first.setStatus(ProjectPhaseStatus.IN_PROGRESS);
                    first.setActualStartDate(LocalDate.now());
                    first.setUpdatedBy(actor(actor));
                });
        project.setCurrentPhaseName(ProjectPhasePlanner.currentPhaseName(project.getPhases()));
    }

    /** Conclusão do projeto: as fases ainda abertas ficam DONE. */
    public void closeOpenPhases(Project project, String actor) {
        LocalDate today = LocalDate.now();
        for (ProjectPhase phase : project.getPhases()) {
            if (phase.getStatus() == ProjectPhaseStatus.NOT_STARTED || phase.getStatus() == ProjectPhaseStatus.IN_PROGRESS) {
                if (phase.getActualStartDate() == null) phase.setActualStartDate(today);
                phase.setStatus(ProjectPhaseStatus.DONE);
                phase.setProgress(100);
                phase.setActualEndDate(today);
                phase.setUpdatedBy(actor(actor));
            }
        }
        project.setCurrentPhaseName(null);
    }

    /**
     * Novo prazo aprovado: as fases não concluídas (nem SKIPPED) são redistribuídas pelo peso, desde o
     * início da primeira delas até à nova data de fim.
     */
    public void redistribute(Project project, LocalDate newEnd) {
        List<ProjectPhase> open = project.getPhases().stream()
                .filter(p -> p.getStatus() == ProjectPhaseStatus.NOT_STARTED || p.getStatus() == ProjectPhaseStatus.IN_PROGRESS)
                .sorted((a, b) -> a.getSequence().compareTo(b.getSequence()))
                .toList();
        if (open.isEmpty() || newEnd == null) {
            return;
        }
        ProjectPhase first = open.get(0);
        LocalDate start = first.getActualStartDate() != null ? first.getActualStartDate()
                : first.getPlannedStartDate() != null ? first.getPlannedStartDate()
                : project.getExecution() == null ? null : project.getExecution().getPlannedStartDate();
        if (start == null) {
            start = LocalDate.now();
        }
        if (start.isAfter(newEnd)) {
            start = newEnd;
        }
        ProjectPhasePlanner.distribute(open, start, newEnd);
    }

    /** Recalcula {@code execution.progress} e a fase atual do projeto a partir das fases. */
    public Integer recalculate(Project project, String actor) {
        List<ProjectPhase> all = phases.findByProjectIdOrderBySequenceAsc(project.getId());
        Integer progress = ProjectPhasePlanner.weightedProgress(all);
        project.setCurrentPhaseName(ProjectPhasePlanner.currentPhaseName(all));
        if (progress != null) {
            ProjectExecution execution = project.getExecution();
            if (execution == null) {
                execution = new ProjectExecution();
                project.attachExecution(execution);
            }
            execution.setProgress(progress);
            execution.setLastUpdatedAt(Instant.now());
            execution.setLastUpdatedBy(actor(actor));
        }
        projects.save(project);
        return progress;
    }

    /** Progresso calculado pelas fases, ou {@code null} se o projeto não tem fases a contar. */
    public Integer phaseProgress(UUID projectId) {
        return ProjectPhasePlanner.weightedProgress(phases.findByProjectIdOrderBySequenceAsc(projectId));
    }

    /**
     * Migração: cria as fases dos projetos que ainda não as têm, da mesma forma que na conversão (planeamento
     * da Demanda de origem; senão o Tipo do projeto). Idempotente.
     */
    public int backfillMissingPhases() {
        int created = 0;
        for (Project project : projects.findAll()) {
            if (!project.getPhases().isEmpty()) {
                continue;
            }
            List<ProjectPhasePlanner.PhaseSeed> seeds = ProjectPhasePlanner.seedsFromDemand(project.getSourceDemand());
            if (seeds.isEmpty()) {
                seeds = ProjectPhasePlanner.seedsFromModel(project.getPhaseModel());
            }
            if (seeds.isEmpty()) {
                continue;
            }
            createPhases(project, seeds, "system");
            if (project.getStatus() == cvt.cv.ppmbackend.enums.ProjectStatus.IN_PROGRESS) {
                startFirstPhase(project, "system");
            } else if (ProjectGuard.isClosed(project)
                    && project.getStatus() == cvt.cv.ppmbackend.enums.ProjectStatus.COMPLETED) {
                closeOpenPhases(project, "system");
            }
            projects.save(project);
            created++;
        }
        if (created > 0) {
            log.info("Plano por fases criado para {} projeto(s) existente(s)", created);
        }
        return created;
    }

    static PhaseResponse toResponse(ProjectPhase p) {
        return new PhaseResponse(p.getId(), p.getName(), p.getSequence(), p.getWeight(), p.getPlannedStartDate(),
                p.getPlannedEndDate(), p.getActualStartDate(), p.getActualEndDate(), p.getStatus(), p.getProgress(),
                p.getUpdatedBy(), p.getUpdatedAt());
    }

    private static String label(ProjectPhaseStatus status) {
        return switch (status) {
            case NOT_STARTED -> "por iniciar";
            case IN_PROGRESS -> "em curso";
            case DONE -> "concluída";
            case SKIPPED -> "não aplicável";
        };
    }

    private static int nz(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
