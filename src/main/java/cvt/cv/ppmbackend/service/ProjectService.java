package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.*;
import cvt.cv.ppmbackend.dto.ProjectExecutionRankDtos.ReprioritizeRequest;
import cvt.cv.ppmbackend.dto.ProjectExecutionRankDtos.ReprioritizeResponse;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.*;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class ProjectService {
    private static final List<String> DOMAIN_CATEGORIES =
            List.of("DOMAIN", "PROJECT_DOMAIN", "DEMAND_DOMAIN", "PROGRAM_DOMAIN");

    private final ProjectRepository projects;
    private final ProjectExecutionRankHistoryRepository rankHistory;
    private final LookupValueRepository lookups;
    private final SupplierRepository suppliers;
    private final ProgramRepository programs;
    private final StrategicPlanRepository strategicPlans;
    private final OperationalPlanRepository operationalPlans;
    private final StrategicPillarRepository strategicPillars;
    private final StrategicObjectiveRepository strategicObjectives;
    private final DemandProjectInheritanceService inheritance;
    private final ProjectPhaseService phases;
    private final ProjectEventService events;
    private final ProjectClosureService closures;
    private final ProjectGuard guard;
    private final PhaseModelRepository phaseModels;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectCostRepository costs;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectBaselineService baselines;

    public ProjectService(ProjectRepository projects, ProjectExecutionRankHistoryRepository rankHistory,
            LookupValueRepository lookups, SupplierRepository suppliers, ProgramRepository programs,
            StrategicPlanRepository strategicPlans, OperationalPlanRepository operationalPlans,
            StrategicPillarRepository strategicPillars, StrategicObjectiveRepository strategicObjectives,
            DemandProjectInheritanceService inheritance, ProjectPhaseService phases, ProjectEventService events,
            ProjectClosureService closures, ProjectGuard guard, PhaseModelRepository phaseModels) {
        this.inheritance = inheritance;
        this.phases = phases;
        this.events = events;
        this.closures = closures;
        this.guard = guard;
        this.phaseModels = phaseModels;
        this.projects = projects;
        this.rankHistory = rankHistory;
        this.lookups = lookups;
        this.suppliers = suppliers;
        this.programs = programs;
        this.strategicPlans = strategicPlans;
        this.operationalPlans = operationalPlans;
        this.strategicPillars = strategicPillars;
        this.strategicObjectives = strategicObjectives;
    }

    @Transactional(readOnly = true)
    public Project findById(UUID id) {
        return projects.findById(id).orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        Project project = findById(id);
        if (!guard.canView(project)) throw new ResourceNotFoundException("Projeto não encontrado: " + id);
        return ProjectResponse.detailed(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectListItemResponse> list(int page, int size, ProjectOrigin origin,
            String directionCode, String areaCode, String domain, ProjectStatus status, RiskLevel risk) {
        Specification<Project> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (origin != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("origin"), origin));
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (hasText(directionCode)) spec = spec.and(textEquals("directionCode", directionCode));
        if (hasText(areaCode)) spec = spec.and(textEquals("areaCode", areaCode));
        if (hasText(domain)) {
            String value = domain.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.equal(cb.upper(r.join("domain", JoinType.INNER).get("code")), value),
                    cb.equal(cb.upper(r.join("domain", JoinType.INNER).get("label")), value)));
        }
        if (risk != null) spec = spec.and((r, q, cb) -> cb.equal(r.join("execution", JoinType.INNER).get("risk"), risk));
        if (guard.filterEnabled()) {
            UserAccess access = guard.currentUser();
            if (!access.has(UserAccess.PROJECT_VIEW_ALL) && !access.has(UserAccess.PROJECT_EDIT_ALL)) {
                spec = spec.and((r, q, cb) -> cb.equal(cb.lower(r.get("projectManagerUsername")), access.userName()));
            }
        }
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("executionRank").ascending());
        var result = projects.findAll(spec, pageable);
        return new PageResponse<>(result.map(ProjectListItemResponse::from).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public ProjectResponse createExtraPlan(ProjectCreateExtraPlanRequest request, String actor) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode criar projetos extra-plano.");
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        PhaseModel phaseModel = request.phaseModelId() == null ? null
                : phaseModels.findWithPhasesById(request.phaseModelId())
                        .orElseThrow(() -> new BadRequestException("Tipo não encontrado: " + request.phaseModelId()));
        Project project = new Project();
        project.setCode(nextCode());
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOrigin(ProjectOrigin.EXTRA_PLAN);
        project.setDirectionCode(trim(request.directionCode()));
        project.setDirectionName(request.directionName().trim());
        project.setAreaCode(trim(request.areaCode()));
        project.setAreaName(trim(request.areaName()));
        project.setDomain(resolveDomain(request.domain()));
        project.setMainSupplier(entity(suppliers, request.supplierId(), "Fornecedor"));
        project.setImpactedSystem(request.impactedSystem());
        project.setExpectedBenefits(request.expectedBenefits());
        project.setBudget(request.budget());
        project.setStatus(ProjectStatus.PLANNED);
        project.setExtraPlanJustification(request.extraPlanJustification().trim());
        project.setCreatedBy(actor(actor));
        project.setUpdatedBy(actor(actor));
        List<Project> active = projects.findActiveForRanking();
        project.setExecutionRank(active.size() + 1);
        project.attachExecution(newExecution(request.plannedStartDate(), null, request.plannedEndDate(), null, null,
                actor));
        if (hasText(request.projectManager())) project.setProjectManager(request.projectManager().trim());
        if (hasText(request.projectManagerUsername())) {
            project.setProjectManagerUsername(request.projectManagerUsername().trim().toLowerCase(Locale.ROOT));
        }
        project.setPhaseModel(phaseModel);
        // Fases escolhidas pelo utilizador; senão as ativas do Tipo; sem nenhum dos dois, sem fases.
        phases.createPhases(project, request.phases() != null && !request.phases().isEmpty()
                ? ProjectPhaseService.seedsFromPlan(ProjectPhaseService.validatePlan(request.phases()))
                : ProjectPhasePlanner.seedsFromModel(phaseModel), actor(actor));
        setBaseline(project);
        Project saved = projects.save(project);
        events.log(saved, ProjectEventType.CREATED, "Projeto extra-plano criado", actor);
        return ProjectResponse.detailed(saved);
    }

    public ProjectResponse update(UUID id, ProjectUpdateRequest request, String actor) {
        Project p = guard.requireEditable(id);
        if (request == null) throw new BadRequestException("Pedido inválido");
        String previousManager = p.getProjectManager();
        BigDecimal previousBudget = p.getBudget();
        if (request.name() != null) p.setName(request.name());
        if (request.description() != null) p.setDescription(request.description());
        if (request.programId() != null || isClearRequested(request, "programId")) {
            p.setProgram(request.programId() == null ? null : entity(programs, request.programId(), "Programa"));
        }
        if (request.strategicPlanId() != null || isClearRequested(request, "strategicPlanId")) {
            p.setStrategicPlan(request.strategicPlanId() == null ? null
                    : entity(strategicPlans, request.strategicPlanId(), "Plano estratégico"));
        }
        if (request.operationalPlanId() != null || isClearRequested(request, "operationalPlanId")) {
            p.setOperationalPlan(request.operationalPlanId() == null ? null
                    : entity(operationalPlans, request.operationalPlanId(), "Plano operacional"));
        }
        if (request.strategicPillarId() != null || isClearRequested(request, "strategicPillarId")) {
            p.setStrategicPillar(request.strategicPillarId() == null ? null
                    : entity(strategicPillars, request.strategicPillarId(), "Pilar estratégico"));
        }
        if (request.strategicObjectiveId() != null || isClearRequested(request, "strategicObjectiveId")) {
            p.setStrategicObjective(request.strategicObjectiveId() == null ? null
                    : entity(strategicObjectives, request.strategicObjectiveId(), "Objetivo estratégico"));
        }
        if (p.getOrigin() != ProjectOrigin.DEMAND) {
            if (request.directionCode() != null || isClearRequested(request, "directionCode")) {
                p.setDirectionCode(trim(request.directionCode()));
            }
            if (request.directionName() != null || isClearRequested(request, "directionName")) {
                p.setDirectionName(request.directionName() == null ? null : request.directionName().trim());
            }
            if (request.areaCode() != null || isClearRequested(request, "areaCode")) {
                p.setAreaCode(trim(request.areaCode()));
            }
            if (request.areaName() != null || isClearRequested(request, "areaName")) {
                p.setAreaName(request.areaName() == null ? null : request.areaName().trim());
            }
        }
        if (request.domain() != null) p.setDomain(resolveDomain(request.domain()));
        if (request.supplierId() != null || isClearRequested(request, "supplierId")) {
            p.setMainSupplier(request.supplierId() == null ? null : entity(suppliers, request.supplierId(), "Fornecedor"));
        }
        if (request.impactedSystem() != null || isClearRequested(request, "impactedSystem")) {
            p.setImpactedSystem(request.impactedSystem());
        }
        if (request.expectedBenefits() != null || isClearRequested(request, "expectedBenefits")) {
            p.setExpectedBenefits(request.expectedBenefits());
        }
        if (request.budget() != null || isClearRequested(request, "budget")) {
            p.setBudget(request.budget());
        }
        if (request.projectPhaseId() != null || isClearRequested(request, "projectPhaseId")) {
            p.setProjectPhase(request.projectPhaseId() == null ? null : entity(lookups, request.projectPhaseId(), "Fase do projeto"));
        }
        if (request.projectManager() != null || isClearRequested(request, "projectManager")) {
            p.setProjectManager(request.projectManager() == null ? null : request.projectManager().trim());
        }
        if (request.projectManagerUsername() != null || isClearRequested(request, "projectManagerUsername")) {
            p.setProjectManagerUsername(request.projectManagerUsername() == null ? null
                    : request.projectManagerUsername().trim().toLowerCase(Locale.ROOT));
        }
        if (request.extraPlanJustification() != null || isClearRequested(request, "extraPlanJustification")) {
            p.setExtraPlanJustification(trim(request.extraPlanJustification()));
        }
        if (p.getOrigin() == ProjectOrigin.EXTRA_PLAN && !hasText(p.getExtraPlanJustification())) {
            throw new BadRequestException("extraPlanJustification é obrigatória para projeto EXTRA_PLAN");
        }
        if (p.getOrigin() != ProjectOrigin.DEMAND && !hasText(p.getDirectionName())) {
            throw new BadRequestException("directionName é obrigatória para projeto não originado de demanda");
        }
        p.setUpdatedBy(actor(actor));
        ProjectExecution exec = p.getExecution();
        if (exec != null) {
            if (request.progress() != null) exec.setProgress(request.progress());
            if (request.consumedBudget() != null) {
                if (costs != null && costs.existsByProjectId(id)) {
                    throw new BadRequestException("O orçamento consumido é calculado a partir dos custos do projeto.");
                }
                exec.setConsumedBudget(request.consumedBudget());
            }
            if (request.plannedStartDate() != null) exec.setPlannedStartDate(request.plannedStartDate());
            if (request.actualStartDate() != null) exec.setActualStartDate(request.actualStartDate());
            if (request.plannedEndDate() != null) exec.setPlannedEndDate(request.plannedEndDate());
            if (request.actualEndDate() != null) exec.setActualEndDate(request.actualEndDate());
            if (request.scheduleStatus() != null) exec.setScheduleStatus(request.scheduleStatus());
            if (request.costStatus() != null) exec.setCostStatus(request.costStatus());
            if (request.riskStatus() != null) exec.setRiskStatus(request.riskStatus());
            if (request.valueStatus() != null) exec.setValueStatus(request.valueStatus());
            if (request.risk() != null) exec.setRisk(request.risk());
            if (request.delayReasons() != null) exec.setDelayReasons(request.delayReasons());
            if (request.executionNotes() != null) exec.setExecutionNotes(request.executionNotes());
            exec.setLastUpdatedBy(actor(actor));
            exec.setLastUpdatedAt(java.time.Instant.now());
        }
        if (exec != null) validateDates(exec.getPlannedStartDate(), exec.getPlannedEndDate());
        Project saved = projects.save(p);
        if (!Objects.equals(trim(previousManager), trim(saved.getProjectManager()))) {
            events.log(saved, ProjectEventType.MANAGER_CHANGED, "Gestor do projeto: "
                    + Objects.requireNonNullElse(trim(previousManager), "sem gestor") + " → "
                    + Objects.requireNonNullElse(trim(saved.getProjectManager()), "sem gestor"), actor);
        }
        if (!sameAmount(previousBudget, saved.getBudget())) {
            events.log(saved, ProjectEventType.BUDGET_CHANGED, "Orçamento alterado: "
                    + ProjectEventService.money(previousBudget) + " → "
                    + ProjectEventService.money(saved.getBudget()), actor);
        }
        events.log(saved, ProjectEventType.PROJECT_UPDATED, "Dados do projeto atualizados", actor);
        return ProjectResponse.detailed(saved);
    }

    public Project saveFromDemand(Project p, Demand demand, DemandPlanningCycle cycle, LocalDate plannedStart,
            LocalDate actualStart, LocalDate plannedEnd, LocalDate actualEnd, String delayReasons, String actor) {
        validateDates(plannedStart, plannedEnd);
        if (p.getCode() == null) p.setCode(nextCode());
        p.setOrigin(ProjectOrigin.DEMAND);
        p.setSourceDemand(demand);
        // Demandas enviadas para backlog geram sempre projetos em BACKLOG, nunca em PLANNED.
        if (Boolean.TRUE.equals(p.getCreatedFromDemandBacklog())) p.setStatus(ProjectStatus.BACKLOG);
        else if (p.getStatus() == null) p.setStatus(ProjectStatus.PLANNED);
        p.setExecutionRank(cycle.getPortfolioRank());
        p.setSourceDemandPortfolioRank(cycle.getPortfolioRank());
        if (p.getBudget() == null) p.setBudget(demand.getEstimatedBudget());
        if (!hasText(p.getExpectedBenefits())) p.setExpectedBenefits(demand.getDesiredOutcome());
        if (p.getStrategicPlan() == null) p.setStrategicPlan(cycle.getStrategicPlan());
        if (p.getOperationalPlan() == null) p.setOperationalPlan(cycle.getOperationalPlan());
        if (p.getStrategicPillar() == null) p.setStrategicPillar(cycle.getStrategicPillar());
        if (p.getStrategicObjective() == null) p.setStrategicObjective(cycle.getStrategicObjective());
        if (!hasText(p.getDirectionName())) {
            p.setDirectionName(firstNonBlank(demand.getDirectionName(), demand.getDirectionCode()));
            p.setDirectionCode(hasText(demand.getDirectionName()) ? demand.getDirectionCode() : null);
        }
        if (!hasText(p.getAreaName())) {
            p.setAreaName(firstNonBlank(demand.getAreaName(), demand.getAreaCode()));
            p.setAreaCode(hasText(demand.getAreaName()) ? demand.getAreaCode() : null);
        }
        if (!hasText(p.getDirectionName())) throw new BadRequestException("directionName é obrigatória no projeto");
        inheritance.inherit(p, demand);
        p.getProfiles().forEach(profile -> {
            profile.setCreatedBy(actor(actor));
            profile.setUpdatedBy(actor(actor));
        });
        // Sem datas na conversão, o calendário vem da Demanda — só quando é coerente, para nunca falhar aqui.
        if (plannedEnd == null) {
            LocalDate demandEnd = demand.getDesiredDate() != null ? demand.getDesiredDate() : demand.getMandatoryDeadline();
            if (demandEnd != null && (plannedStart == null || !demandEnd.isBefore(plannedStart))) plannedEnd = demandEnd;
        }
        if (plannedStart == null && demand.getPlannedStartDate() != null
                && (plannedEnd == null || !demand.getPlannedStartDate().isAfter(plannedEnd))) {
            plannedStart = demand.getPlannedStartDate();
        }
        p.setCreatedBy(actor(actor));
        p.setUpdatedBy(actor(actor));
        p.attachExecution(newExecution(plannedStart, actualStart, plannedEnd, actualEnd, delayReasons, actor));
        phases.createPhases(p, ProjectPhasePlanner.seedsFromDemand(demand), actor(actor));
        phases.linkProfilesByName(p);
        setBaseline(p);
        Project saved = projects.save(p);
        events.log(saved, ProjectEventType.CREATED, "Projeto criado a partir da demanda " + demand.getCode(), actor);
        return saved;
    }

    /** Orçamento e fim previstos na criação, antes de qualquer pedido de alteração. */
    private static void setBaseline(Project project) {
        if (project.getBaselineBudget() == null) project.setBaselineBudget(project.getBudget());
        if (project.getBaselineEndDate() == null && project.getExecution() != null) {
            project.setBaselineEndDate(project.getExecution().getPlannedEndDate());
        }
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public ReprioritizeResponse reprioritize(UUID id, ReprioritizeRequest request, String actor) {
        List<Project> active = projects.findActiveForRanking();
        Project target = active.stream().filter(p -> p.getId().equals(id)).findFirst()
                .orElseThrow(() -> new BadRequestException("Apenas projetos ativos podem ser repriorizados"));
        int previous = target.getExecutionRank() == null ? active.indexOf(target) + 1 : target.getExecutionRank();
        active.remove(target);
        int position = Math.min(request.newPosition(), active.size() + 1);
        active.add(position - 1, target);
        for (int i = 0; i < active.size(); i++) active.get(i).setExecutionRank(i + 1);
        projects.saveAll(active);
        ProjectExecutionRankHistory entry = new ProjectExecutionRankHistory();
        entry.setProject(target);
        entry.setPreviousRank(previous);
        entry.setNewRank(position);
        entry.setReason(request.reason());
        entry.setJustification(request.justification().trim());
        entry.setChangedBy(actor(actor));
        rankHistory.save(entry);
        return new ReprioritizeResponse(id, previous, position);
    }

    public ProjectResponse updateStatus(UUID id, ProjectStatus status, String actor) {
        Project p = findById(id);
        ProjectStatus previous = p.getStatus();
        p.setStatus(status);
        p.setUpdatedBy(actor(actor));
        Project saved = projects.save(p);
        if (previous != status) {
            events.log(saved, ProjectEventType.STATUS_CHANGED, "Estado do projeto: " + statusLabel(previous)
                    + " → " + statusLabel(status), actor);
        }
        return ProjectResponse.detailed(saved);
    }

    private static String statusLabel(ProjectStatus status) {
        if (status == null) return "sem estado";
        return switch (status) {
            case BACKLOG -> "backlog";
            case PLANNED -> "planeado";
            case IN_PROGRESS -> "em curso";
            case ON_HOLD -> "suspenso";
            case COMPLETED -> "concluído";
            case CANCELLED -> "cancelado";
        };
    }

    public ProjectResponse start(UUID id, String actor) {
        Project project = findById(id);
        if (project.getStatus() != ProjectStatus.PLANNED) {
            throw new BadRequestException("Apenas projetos planeados podem ser iniciados");
        }

        String updatedBy = actor(actor);
        ProjectExecution execution = project.getExecution();
        if (execution == null) {
            execution = newExecution(null, LocalDate.now(), null, null, null, updatedBy);
            project.attachExecution(execution);
        } else {
            execution.setActualStartDate(LocalDate.now());
            execution.setLastUpdatedAt(java.time.Instant.now());
            execution.setLastUpdatedBy(updatedBy);
        }

        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setUpdatedBy(updatedBy);
        phases.startFirstPhase(project, updatedBy);
        Project saved = projects.save(project);
        if (baselines != null) baselines.captureIfAbsent(saved, "Início do projeto", actor);
        events.log(saved, ProjectEventType.STARTED, "Projeto iniciado"
                + (saved.getCurrentPhaseName() == null ? "" : " na fase " + saved.getCurrentPhaseName()), actor);
        return ProjectResponse.detailed(saved);
    }

    public ProjectResponse complete(UUID id, String actor) {
        return complete(id, null, actor);
    }

    /**
     * Conclusão. Com {@code closure}, exige resumo e pelo menos uma lição, e grava o relatório final. As fases
     * ainda abertas ficam concluídas e o progresso passa a 100.
     */
    public ProjectResponse complete(UUID id, ProjectManagementDtos.ClosureRequest closure, String actor) {
        Project project = findById(id);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new BadRequestException("Apenas projetos em progresso podem ser concluídos");
        }
        if (closure != null) {
            closures.validate(closure);
        }

        LocalDate completionDate = LocalDate.now();
        String updatedBy = actor(actor);
        ProjectExecution execution = project.getExecution();
        if (execution == null) {
            execution = newExecution(null, null, null, completionDate, null, updatedBy);
            project.attachExecution(execution);
        } else {
            if (execution.getActualStartDate() != null && execution.getActualStartDate().isAfter(completionDate)) {
                throw new BadRequestException("Data de conclusão não pode ser anterior à data de início");
            }
            execution.setActualEndDate(completionDate);
            execution.setLastUpdatedAt(java.time.Instant.now());
            execution.setLastUpdatedBy(updatedBy);
        }
        execution.setProgress(100);

        phases.closeOpenPhases(project, updatedBy);
        project.setStatus(ProjectStatus.COMPLETED);
        project.setUpdatedBy(updatedBy);
        Project saved = projects.save(project);
        if (closure != null) {
            closures.record(saved, closure, updatedBy);
        }
        events.log(saved, ProjectEventType.COMPLETED, "Projeto concluído"
                + (closure == null ? "" : " com relatório de encerramento"), actor);
        return ProjectResponse.detailed(saved);
    }

    public void delete(UUID id) {
        Project p = findById(id);
        projects.delete(p);
        projects.flush();
        List<Project> active = projects.findActiveForRanking();
        for (int i = 0; i < active.size(); i++) active.get(i).setExecutionRank(i + 1);
        projects.saveAll(active);
    }

    @Transactional(readOnly = true)
    public List<Project> findByStatus(ProjectStatus status) { return projects.findByStatus(status); }
    @Transactional(readOnly = true)
    public List<Project> findByDomain(UUID id) { return projects.findByDomain_Id(id); }
    @Transactional(readOnly = true)
    public List<Project> findByProgram(UUID id) { return projects.findByProgram_Id(id); }
    @Transactional(readOnly = true)
    public List<Project> findByOperationalPlan(UUID id) { return projects.findByOperationalPlan_Id(id); }

    @Transactional(readOnly = true)
    public long countByOperationalPlan(UUID id) { return projects.countByOperationalPlan_Id(id); }

    private ProjectExecution newExecution(LocalDate plannedStart, LocalDate actualStart, LocalDate plannedEnd,
            LocalDate actualEnd, String delayReasons, String actor) {
        ProjectExecution e = new ProjectExecution();
        e.setPlannedStartDate(plannedStart);
        e.setActualStartDate(actualStart);
        e.setPlannedEndDate(plannedEnd);
        e.setActualEndDate(actualEnd);
        e.setDelayReasons(delayReasons);
        e.setLastUpdatedAt(java.time.Instant.now());
        e.setLastUpdatedBy(actor(actor));
        return e;
    }

    private LookupValue resolveDomain(String value) {
        if (!hasText(value)) throw new BadRequestException("domain é obrigatório");
        return lookups.findDomainByCodeOrLabel(DOMAIN_CATEGORIES, value.trim()).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Domínio inválido: " + value));
    }

    private static <T> T entity(org.springframework.data.jpa.repository.JpaRepository<T, UUID> repository, UUID id,
            String label) {
        if (id == null) return null;
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(label + " não encontrado: " + id));
    }

    private Specification<Project> textEquals(String field, String value) {
        return (r, q, cb) -> cb.equal(cb.upper(r.get(field)), value.trim().toUpperCase(Locale.ROOT));
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end))
            throw new BadRequestException("plannedStartDate não pode ser posterior a plannedEndDate");
    }

    private String nextCode() {
        return "PRJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static boolean isClearRequested(ProjectUpdateRequest request, String field) {
        return request != null && request.clear() != null && request.clear().contains(field);
    }

    private static String actor(String actor) { return hasText(actor) ? actor.trim() : "system"; }
    private static String trim(String value) { return hasText(value) ? value.trim() : null; }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String firstNonBlank(String... values) {
        for (String value : values) if (hasText(value)) return value.trim();
        return null;
    }
}
