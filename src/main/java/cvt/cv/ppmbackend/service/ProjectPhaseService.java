package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhasePlanItem;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhasePlanRequest;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhaseResponse;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhaseUpdateRequest;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.enums.ProjectPhaseStatus;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.ProjectMilestoneRepository;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final ProjectMilestoneRepository milestones;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cvt.cv.ppmbackend.repository.ProjectPhaseGateRepository gates;

    public ProjectPhaseService(ProjectPhaseRepository phases, ProjectRepository projects, ProjectGuard guard,
            ProjectEventService events, ProjectMilestoneRepository milestones) {
        this.phases = phases;
        this.projects = projects;
        this.guard = guard;
        this.events = events;
        this.milestones = milestones;
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

        // Avanço só com o projeto em execução: antes disso só se planeia (datas previstas, NOT_STARTED/SKIPPED).
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            boolean advancing = (status != previousStatus
                    && (status == ProjectPhaseStatus.IN_PROGRESS || status == ProjectPhaseStatus.DONE))
                    || (request.progress() != null && request.progress() != nz(phase.getProgress()))
                    || (request.actualStartDate() != null && !request.actualStartDate().equals(phase.getActualStartDate()))
                    || (request.actualEndDate() != null && !request.actualEndDate().equals(phase.getActualEndDate()));
            if (advancing) {
                throw new DomainException(HttpStatus.CONFLICT, "PROJECT_NOT_RUNNING",
                        project.getStatus() == ProjectStatus.ON_HOLD ? "O projeto está suspenso."
                                : "Inicie o projeto antes de registar o avanço das fases.",
                        Map.of("status", project.getStatus().name()));
            }
        }
        if (request.status() == ProjectPhaseStatus.DONE && !gateCriteriaMet(phase)) {
            throw new BadRequestException("Use a passagem de fase.");
        }

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
        project.setCurrentPhaseName(ProjectPhasePlanner.currentPhaseName(all));
        // Fora de execução não há avanço a refletir: execution.progress fica como está.
        Integer progress = project.getStatus() == ProjectStatus.IN_PROGRESS
                ? ProjectPhasePlanner.weightedProgress(all) : null;
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

    /** Conclusão autorizada pelo fluxo stage-gate. */
    public PhaseResponse completeFromGate(Project project, ProjectPhase phase, String actor) {
        LocalDate today = LocalDate.now();
        if (phase.getActualStartDate() == null) phase.setActualStartDate(today);
        phase.setActualEndDate(today);
        phase.setProgress(100);
        phase.setStatus(ProjectPhaseStatus.DONE);
        phase.setUpdatedBy(actor(actor));
        phases.save(phase);
        recalculate(project, actor);
        return toResponse(phase);
    }

    private boolean gateCriteriaMet(ProjectPhase phase) {
        boolean milestonesMet = milestones.findByPhaseId(phase.getId()).stream().allMatch(m ->
                m.isDeliverable() ? m.getStatus() == cvt.cv.ppmbackend.enums.MilestoneStatus.DONE
                        : m.getStatus() == cvt.cv.ppmbackend.enums.MilestoneStatus.DONE
                                || m.getStatus() == cvt.cv.ppmbackend.enums.MilestoneStatus.CANCELLED);
        if (!milestonesMet || gates == null) return milestonesMet;
        return gates.findByPhaseId(phase.getId()).map(g -> g.getChecklist().stream()
                .allMatch(cvt.cv.ppmbackend.entity.ProjectPhaseGateItem::isMet)).orElse(true);
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

    // ----- Personalizar o plano -----

    /**
     * Substitui o plano inteiro: fases com {@code id} são atualizadas (nome, peso, ordem) mantendo o avanço;
     * sem {@code id} são criadas por iniciar; as ausentes são removidas, se não tiverem avanço.
     */
    public List<PhaseResponse> replacePlan(UUID projectId, PhasePlanRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        List<PhasePlanItem> items = validatePlan(request == null ? null : request.phases());
        Map<UUID, ProjectPhase> current = new HashMap<>();
        project.getPhases().forEach(p -> current.put(p.getId(), p));
        for (PhasePlanItem item : items) {
            if (item.id() != null && !current.containsKey(item.id())) {
                throw new BadRequestException("A fase " + item.id() + " não pertence a este projeto.");
            }
        }
        Set<UUID> kept = new HashSet<>();
        items.stream().map(PhasePlanItem::id).filter(Objects::nonNull).forEach(kept::add);
        List<ProjectPhase> removed = project.getPhases().stream().filter(p -> !kept.contains(p.getId())).toList();
        for (ProjectPhase phase : removed) {
            boolean untouched = (phase.getStatus() == ProjectPhaseStatus.NOT_STARTED
                    || phase.getStatus() == ProjectPhaseStatus.SKIPPED) && nz(phase.getProgress()) == 0;
            if (!untouched) {
                throw new DomainException(HttpStatus.CONFLICT, "PHASE_HAS_PROGRESS",
                        "A fase " + phase.getName() + " tem avanço registado: não pode ser removida.",
                        Map.of("phaseId", phase.getId().toString()));
            }
        }

        List<String> changes = new ArrayList<>();
        for (ProjectPhase phase : removed) {
            milestones.findByPhaseId(phase.getId()).forEach(m -> m.setPhase(null));
            changes.add("removida " + phase.getName());
        }
        milestones.flush();
        // As fases removidas deixam de estar ligadas aos perfis; quem ficar sem fases passa a "todo o projeto".
        List<String> clearedProfiles = new ArrayList<>();
        for (ProjectProfile profile : project.getProfiles()) {
            boolean hadPhases = !profile.getPhases().isEmpty();
            if (profile.getPhases().removeAll(removed) && hadPhases && profile.getPhases().isEmpty()) {
                clearedProfiles.add(profile.getProfileName());
            }
        }
        project.getPhases().removeAll(removed);

        List<ProjectPhase> ordered = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            PhasePlanItem item = items.get(i);
            BigDecimal weight = item.weight().setScale(2, java.math.RoundingMode.HALF_UP);
            ProjectPhase phase;
            if (item.id() == null) {
                phase = new ProjectPhase();
                phase.setProject(project);
                phase.setStatus(ProjectPhaseStatus.NOT_STARTED);
                phase.setProgress(0);
                phase.setCreatedBy(actor(actor));
                project.getPhases().add(phase);
                changes.add("acrescentada " + item.name() + " (" + plain(weight) + "%)");
            } else {
                phase = current.get(item.id());
                if (!phase.getName().equals(item.name())) {
                    changes.add("renomeada " + phase.getName() + " → " + item.name());
                }
                if (phase.getWeight() == null || phase.getWeight().compareTo(weight) != 0) {
                    changes.add("peso de " + item.name() + ": " + plain(phase.getWeight()) + "% → " + plain(weight) + "%");
                }
            }
            phase.setName(item.name());
            phase.setWeight(weight);
            phase.setSequence(i + 1);
            phase.setUpdatedBy(actor(actor));
            ordered.add(phase);
        }

        if (request.redistributeDates() == null || request.redistributeDates()) {
            redistributeOpenPhases(project, ordered);
        }
        // A ligação é por id: renomear não a quebra, só atualiza a cache dos nomes.
        project.getProfiles().forEach(ProjectProfileService::refreshNameCache);
        for (String profileName : clearedProfiles) {
            events.log(project, ProjectEventType.PROFILE_PHASES_CLEARED, "Perfil " + profileName
                    + " passa a contar para todo o projeto: as suas fases foram removidas do plano", actor);
        }
        projects.save(project);
        phases.flush();
        recalculate(project, actor);

        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        events.log(project, ProjectEventType.PLAN_CHANGED, "Plano de fases alterado: "
                + (changes.isEmpty() ? "ordem das fases" : String.join("; ", changes))
                + (reason == null ? "" : ". Motivo: " + reason), actor);
        return phases.findByProjectIdOrderBySequenceAsc(projectId).stream().map(ProjectPhaseService::toResponse).toList();
    }

    /**
     * DONE e SKIPPED mantêm as datas; as outras repartem, pelo peso e pela ordem, os dias entre o dia a seguir ao
     * fim da última fase DONE (ou o início previsto, se for mais tarde) e o fim previsto do projeto.
     */
    private static void redistributeOpenPhases(Project project, List<ProjectPhase> ordered) {
        LocalDate plannedStart = project.getExecution() == null ? null : project.getExecution().getPlannedStartDate();
        LocalDate plannedEnd = project.getExecution() == null ? null : project.getExecution().getPlannedEndDate();
        LocalDate afterDone = ordered.stream()
                .filter(p -> p.getStatus() == ProjectPhaseStatus.DONE)
                .map(p -> p.getActualEndDate() != null ? p.getActualEndDate() : p.getPlannedEndDate())
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .map(d -> d.plusDays(1))
                .orElse(null);
        LocalDate start = afterDone == null ? plannedStart
                : plannedStart == null || afterDone.isAfter(plannedStart) ? afterDone : plannedStart;
        if (start != null && plannedEnd != null && start.isAfter(plannedEnd)) {
            start = plannedEnd;
        }
        List<ProjectPhase> open = ordered.stream()
                .filter(p -> p.getStatus() != ProjectPhaseStatus.DONE && p.getStatus() != ProjectPhaseStatus.SKIPPED)
                .toList();
        ProjectPhasePlanner.distribute(open, start, plannedEnd);
    }

    /**
     * Validação comum ao plano personalizado e à criação extra-plano: pelo menos uma fase, nomes preenchidos e
     * únicos, pesos positivos a somar 100 (±0,01). Devolve os itens com os nomes aparados.
     */
    public static List<PhasePlanItem> validatePlan(List<PhasePlanItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("O plano precisa de pelo menos uma fase.");
        }
        List<PhasePlanItem> normalized = new ArrayList<>();
        Set<String> names = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PhasePlanItem item : items) {
            String name = item == null || item.name() == null ? null : item.name().trim();
            if (name == null || name.isEmpty()) {
                throw new BadRequestException("Todas as fases precisam de nome.");
            }
            if (name.length() > 150) {
                throw new BadRequestException("O nome da fase não pode exceder 150 caracteres.");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("A fase " + name + " está repetida.");
            }
            if (item.weight() == null || item.weight().signum() <= 0) {
                throw new BadRequestException("O peso da fase " + name + " tem de ser maior que zero.");
            }
            total = total.add(item.weight());
            normalized.add(new PhasePlanItem(item.id(), name, item.weight()));
        }
        if (total.subtract(BigDecimal.valueOf(100)).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BadRequestException("Os pesos somam " + plain(total) + "%: têm de somar 100%.");
        }
        return normalized;
    }

    /** Fases para a criação extra-plano a partir da lista escolhida pelo utilizador (já validada). */
    public static List<ProjectPhasePlanner.PhaseSeed> seedsFromPlan(List<PhasePlanItem> items) {
        List<ProjectPhasePlanner.PhaseSeed> seeds = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            seeds.add(new ProjectPhasePlanner.PhaseSeed(items.get(i).name(), i + 1, items.get(i).weight()));
        }
        return seeds;
    }

    /**
     * Migração: projetos ainda não iniciados (BACKLOG/PLANNED) com fases já em curso ou concluídas voltam a ter
     * as fases por iniciar. Idempotente.
     */
    public int resetPhasesOfProjectsNotStarted() {
        int reset = 0;
        for (Project project : projects.findAll()) {
            if (project.getStatus() != ProjectStatus.BACKLOG && project.getStatus() != ProjectStatus.PLANNED) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (ProjectPhase phase : project.getPhases()) {
                boolean advanced = phase.getStatus() == ProjectPhaseStatus.IN_PROGRESS
                        || phase.getStatus() == ProjectPhaseStatus.DONE || nz(phase.getProgress()) > 0
                        || phase.getActualStartDate() != null || phase.getActualEndDate() != null;
                if (advanced) {
                    if (phase.getStatus() != ProjectPhaseStatus.SKIPPED) {
                        phase.setStatus(ProjectPhaseStatus.NOT_STARTED);
                    }
                    phase.setProgress(0);
                    phase.setActualStartDate(null);
                    phase.setActualEndDate(null);
                    phase.setUpdatedBy("system");
                    names.add(phase.getName());
                }
            }
            if (!names.isEmpty()) {
                project.setCurrentPhaseName(null);
                projects.save(project);
                events.log(project, ProjectEventType.PHASE_UPDATED, "Fases repostas por iniciar ("
                        + String.join(", ", names) + "): o projeto ainda não foi iniciado", "system");
                reset++;
            }
        }
        if (reset > 0) {
            log.info("Fases repostas por iniciar em {} projeto(s) ainda não iniciado(s)", reset);
        }
        return reset;
    }

    /**
     * Liga às fases do projeto, pelo nome (sem distinguir maiúsculas nem espaços), os perfis que ainda só têm
     * {@code phase_names}. Usado na conversão da Demanda e na migração. Devolve os nomes sem correspondência.
     */
    public List<String> linkProfilesByName(Project project) {
        List<String> unmatched = new ArrayList<>();
        if (project.getPhases().isEmpty()) {
            return unmatched;
        }
        Map<String, ProjectPhase> byName = new HashMap<>();
        project.getPhases().forEach(p -> byName.putIfAbsent(ProjectProfileService.nameKey(p.getName()), p));
        for (ProjectProfile profile : project.getProfiles()) {
            List<String> names = ProjectProfileService.splitPhases(profile.getPhaseNames());
            if (!profile.getPhases().isEmpty() || names.isEmpty()) {
                continue;
            }
            List<ProjectPhase> linked = new ArrayList<>();
            for (String name : names) {
                ProjectPhase phase = byName.get(ProjectProfileService.nameKey(name));
                if (phase == null) {
                    unmatched.add(profile.getProfileName() + " → " + name);
                } else if (!linked.contains(phase)) {
                    linked.add(phase);
                }
            }
            ProjectProfileService.link(profile, linked);
        }
        return unmatched;
    }

    /** Migração: liga os perfis existentes às fases do seu projeto. Idempotente. */
    public int linkExistingProfiles() {
        int linked = 0;
        for (Project project : projects.findAll()) {
            boolean pending = project.getProfiles().stream()
                    .anyMatch(p -> p.getPhases().isEmpty() && p.getPhaseNames() != null && !p.getPhaseNames().isBlank());
            if (!pending || project.getPhases().isEmpty()) {
                continue;
            }
            List<String> unmatched = linkProfilesByName(project);
            if (!unmatched.isEmpty()) {
                log.warn("Projeto {}: fases dos perfis sem correspondência no plano (não ligadas): {}",
                        project.getCode(), unmatched);
            }
            projects.save(project);
            linked++;
        }
        if (linked > 0) {
            log.info("Perfis ligados às fases do projeto em {} projeto(s)", linked);
        }
        return linked;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
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
