package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.ConditionalApprovalResponse;
import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.FinalApprovalResponse;
import cvt.cv.ppmbackend.dto.StrategicPlanCreateRequest;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.ConfigurationSummary;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.Metrics;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.NextPeriod;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.Overview;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.PlanListItem;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.PlanSummary;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.PlanningPlanSummary;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.PlanningProgress;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
@Transactional
public class StrategicPlanService extends AbstractCrudService<StrategicPlan, StrategicPlanCreateRequest> {
    private static final List<StrategicPlanStatus> PLANNING_STATUSES = List.of(
            StrategicPlanStatus.DRAFT, StrategicPlanStatus.CONDITIONALLY_APPROVED, StrategicPlanStatus.APPROVED);
    /** Demandas selecionadas pelo cenário final — as que geram projetos ao aprovar o plano. */
    private static final List<DemandPlanningStatus> APPROVED_CYCLE_STATUSES = List.of(IN_PLAN);
    /** Tudo o que não entrou no plano gera projeto em backlog. */
    private static final List<DemandPlanningStatus> BACKLOG_CYCLE_STATUSES = List.of(DRAFT, IN_ANALYSIS,
            IN_REVIEW, IN_PRIORITIZATION, PRIORITIZED, BACKLOG);

    private final StrategicPlanRepository plans;
    private final DemandPlanningCycleRepository planningCycles;
    private final StrategicObjectiveRepository objectives;
    private final ProgramRepository programs;
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final OperationalPlanRepository operationalPlans;
    private final DemandHistoryService historyService;
    private final DemandScoreLifecycleService scoreLifecycle;
    private final StrategicPlanningConfigService planningConfig;

    public StrategicPlanService(StrategicPlanRepository r, DemandPlanningCycleRepository planningCycles,
            StrategicObjectiveRepository objectives, ProgramRepository programs, ProjectService projects,
            ProjectRepository projectRepository, OperationalPlanRepository operationalPlans,
            DemandHistoryService historyService, DemandScoreLifecycleService scoreLifecycle,
            StrategicPlanningConfigService planningConfig) {
        super(r, "Plano estratégico");
        this.plans = r;
        this.planningCycles = planningCycles;
        this.objectives = objectives;
        this.programs = programs;
        this.projects = projects;
        this.projectRepository = projectRepository;
        this.operationalPlans = operationalPlans;
        this.historyService = historyService;
        this.scoreLifecycle = scoreLifecycle;
        this.planningConfig = planningConfig;
    }

    protected StrategicPlan newEntity() {
        return new StrategicPlan();
    }

    protected StrategicPlan apply(StrategicPlanCreateRequest r, StrategicPlan e) {
        if (r.endYear() < r.startYear())
            throw new BadRequestException("endYear deve ser maior ou igual a startYear");
        e.setName(r.name());
        e.setStartYear(r.startYear());
        e.setEndYear(r.endYear());
        e.setDescription(r.description());
        if (e.getId() == null) {
            e.setStatus(StrategicPlanStatus.DRAFT);
            e.setRevisionNumber(1);
        } else if (e.getStatus() != StrategicPlanStatus.DRAFT
                && (!Objects.equals(r.startYear(), e.getStartYear()) || !Objects.equals(r.endYear(), e.getEndYear()))) {
            throw new BadRequestException("O período do plano só pode ser alterado enquanto estiver em DRAFT.");
        }
        return e;
    }

    @Transactional(readOnly = true)
    public Optional<StrategicPlan> findCurrentPlanningPlan() {
        List<StrategicPlan> planningPlans = plans.findByStatusInAndDeletedAtIsNull(PLANNING_STATUSES);
        if (planningPlans.size() > 1) {
            throw new DomainException(HttpStatus.CONFLICT, "MULTIPLE_PLANS_IN_PLANNING",
                    "Existe mais de um Plano Estratégico em planeamento.", Map.of());
        }
        return planningPlans.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public StrategicPlan getCurrentPlanningPlan() {
        return findCurrentPlanningPlan()
                .orElseThrow(() -> new ResourceNotFoundException("Não existe nenhum Plano Estratégico em planeamento."));
    }

    @Transactional(readOnly = true)
    public Optional<StrategicPlan> findActivePlan() {
        return plans.findFirstByStatusAndDeletedAtIsNull(StrategicPlanStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public StrategicPlan getActivePlan() {
        return findActivePlan()
                .orElseThrow(() -> new ResourceNotFoundException("Não existe nenhum Plano Estratégico ativo."));
    }

    @Override
    @Transactional(readOnly = true)
    public StrategicPlan findById(UUID id) {
        return plans.findById(id)
                .filter(plan -> plan.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Plano estratégico não encontrado: " + id));
    }

    @Override
    public void delete(UUID id) {
        StrategicPlan plan = findById(id);
        Map<String, Long> deps = dependencyCounts(id);
        if (plan.getStatus() != StrategicPlanStatus.DRAFT || deps.values().stream().anyMatch(count -> count > 0)) {
            throw new DomainException(HttpStatus.CONFLICT, "STRATEGIC_PLAN_DELETE_BLOCKED",
                    "Apenas planos em DRAFT e sem dependências podem ser eliminados", new LinkedHashMap<>(deps));
        }
        plan.setDeletedAt(Instant.now());
        plans.save(plan);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID id) {
        StrategicPlan plan = findById(id);
        List<OperationalPlan> operationalPlanList = operationalPlans.findByStrategicPlan_Id(id);
        List<Project> planProjects = operationalPlanList.stream()
                .flatMap(p -> projectRepository.findByOperationalPlan_Id(p.getId()).stream())
                .toList();
        java.math.BigDecimal budget = planProjects.stream()
                .map(Project::getBudget)
                .filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        Map<String, Long> projectsByStatus = new LinkedHashMap<>();
        planProjects.forEach(p -> projectsByStatus.merge(p.getStatus().name(), 1L, Long::sum));

        return Map.ofEntries(
                Map.entry("strategicPlan", toPlanSummary(plan)),
                Map.entry("operationalPlans", operationalPlanList.size()),
                Map.entry("objectives", objectives.findByStrategicPlan_Id(id).size()),
                Map.entry("programs", programs.findByStrategicObjectives_StrategicPlan_Id(id).size()),
                Map.entry("projects", planProjects.size()),
                Map.entry("totalBudget", budget),
                Map.entry("projectsByStatus", projectsByStatus),
                Map.entry("approval", Map.of(
                        "date", Optional.ofNullable(plan.getApprovalDate()).map(Object::toString).orElse(""),
                        "approvedBy", Optional.ofNullable(plan.getApprovedBy()).orElse(""))));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> dependencyCounts(UUID id) {
        long operationalPlanCount = operationalPlans.findByStrategicPlan_Id(id).size();
        long objectiveCount = objectives.findByStrategicPlan_Id(id).size();
        long programCount = programs.findByStrategicObjectives_StrategicPlan_Id(id).size();
        long projectCount = operationalPlans.findByStrategicPlan_Id(id).stream()
                .mapToLong(p -> projectRepository.findByOperationalPlan_Id(p.getId()).size())
                .sum();
        return Map.of("operationalPlans", operationalPlanCount, "objectives", objectiveCount,
                "programs", programCount, "projects", projectCount);
    }

    @Transactional(readOnly = true)
    public List<PlanListItem> history() {
        return plans.findAllByDeletedAtIsNullOrderByStartYearDesc().stream()
                .map(p -> new PlanListItem(p.getId(), p.getName(), p.getStartYear(), p.getEndYear(),
                        p.getHorizonYears(), p.getRevisionNumber(), p.getStatus(), p.getApprovalDate(),
                        p.getApprovedBy(), projectRepository.countByStrategicPlan_Id(p.getId())))
                .toList();
    }

    public StrategicPlan startPlanning(String actorId, String actorName) {
        if (plans.existsByStatusInAndDeletedAtIsNull(PLANNING_STATUSES)) {
            throw new BadRequestException("Já existe um Plano Estratégico em processo de planeamento.");
        }

        StrategicPlanningConfig config = planningConfig.getActive();
        int horizonYears = config.getHorizonYears();

        Optional<StrategicPlan> active = findActivePlan();
        int newStartYear = nextPlanStartYear(active);
        int newEndYear = newStartYear + horizonYears - 1;

        StrategicPlan plan = new StrategicPlan();
        plan.setName("Plano Estratégico " + newStartYear + "–" + newEndYear);
        plan.setStartYear(newStartYear);
        plan.setEndYear(newEndYear);
        plan.setHorizonYears(horizonYears);
        plan.setStatus(StrategicPlanStatus.DRAFT);
        plan.setRevisionNumber(1);
        plan.setApprovalDate(null);
        plan.setApprovedBy(null);
        StrategicPlan savedPlan = plans.save(plan);

        createFirstOperationalPlan(savedPlan, newStartYear);

        return savedPlan;
    }

    private int nextPlanStartYear(Optional<StrategicPlan> activePlan) {
        return activePlan.map(p -> p.getStartYear() + 1).orElseGet(() -> LocalDate.now().getYear() + 1);
    }

    private void createFirstOperationalPlan(StrategicPlan plan, int fiscalYear) {
        operationalPlans.findByStrategicPlan_IdAndFiscalYear(plan.getId(), fiscalYear).ifPresent(existing -> {
            throw new BadRequestException(
                    "Já existe um Plano Operacional para o ano " + fiscalYear + " neste Plano Estratégico.");
        });
        OperationalPlan po = new OperationalPlan();
        po.setName("Plano Operacional " + fiscalYear);
        po.setFiscalYear(fiscalYear);
        po.setStrategicPlan(plan);
        po.setStatus(OperationalPlanStatus.DRAFT);
        po.setVersion(1);
        po.setTotalBudget(java.math.BigDecimal.ZERO);
        operationalPlans.save(po);
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        StrategicPlanningConfig config = planningConfig.getActive();
        Optional<StrategicPlan> activePlan = findActivePlan();
        Optional<StrategicPlan> planningPlan = findCurrentPlanningPlan();

        PlanSummary activeSummary = activePlan.map(this::toPlanSummary).orElse(null);
        PlanningPlanSummary planningSummary = planningPlan.map(p -> new PlanningPlanSummary(
                p.getId(), p.getName(), p.getStartYear(), p.getEndYear(), p.getHorizonYears(),
                p.getRevisionNumber(), p.getStatus(), progressOf(p.getId()))).orElse(null);

        int nextStartYear = nextPlanStartYear(activePlan);
        int nextEndYear = nextStartYear + config.getHorizonYears() - 1;
        NextPeriod nextPeriod = new NextPeriod(nextStartYear, nextEndYear, planningPlan.isEmpty());

        long demandsInScenario = planningPlan.map(p -> planningCycles.findAllByStrategicPlan_Id(p.getId()).size())
                .map(Integer::longValue).orElse(0L);
        long projectsGenerated = planningPlan.map(p -> projectRepository.countByStrategicPlan_Id(p.getId())).orElse(0L);
        long operationalPlanCount = planningPlan.map(p -> operationalPlans.findByStrategicPlan_Id(p.getId()).size())
                .map(Integer::longValue).orElse(0L);
        Metrics metrics = new Metrics(demandsInScenario, projectsGenerated, operationalPlanCount);

        ConfigurationSummary configuration = new ConfigurationSummary(config.isRollingPlan(), config.getHorizonYears(),
                config.getReviewFrequency());

        return new Overview(activeSummary, planningSummary, configuration, nextPeriod, metrics);
    }

    private PlanSummary toPlanSummary(StrategicPlan p) {
        return new PlanSummary(p.getId(), p.getName(), p.getStartYear(), p.getEndYear(), p.getHorizonYears(),
                p.getRevisionNumber(), p.getStatus(), p.getApprovalDate(), p.getApprovedBy());
    }

    private PlanningProgress progressOf(UUID planId) {
        List<DemandPlanningCycle> planCycles = planningCycles.findAllByStrategicPlan_Id(planId);
        long total = planCycles.size();
        long approved = planCycles.stream().filter(c -> c.getStatus() == IN_PLAN).count();
        long conditionallyApproved = planCycles.stream().filter(c -> c.getStatus() == IN_REVIEW).count();
        long backlog = planCycles.stream().filter(c -> c.getStatus() == BACKLOG).count();
        long deliberated = approved + backlog;
        long pending = total - deliberated;
        int deliberationPercentage = total == 0 ? 0 : (int) Math.round(deliberated * 100.0 / total);
        long projectsGenerated = projectRepository.countByStrategicPlan_Id(planId);
        return new PlanningProgress(total, deliberated, approved, conditionallyApproved, backlog, pending,
                deliberationPercentage, projectsGenerated);
    }

    public StrategicPlan activate(UUID id) {
        StrategicPlan target = findById(id);
        if (target.getStatus() != StrategicPlanStatus.APPROVED)
            throw new BadRequestException("Somente um plano APROVADO pode ser ativado.");
        plans.findFirstByStatus(StrategicPlanStatus.ACTIVE).ifPresent(current -> {
            current.setStatus(StrategicPlanStatus.SUPERSEDED);
            plans.save(current);
        });
        target.setStatus(StrategicPlanStatus.ACTIVE);
        return plans.save(target);
    }

    public StrategicPlan close(UUID id) {
        StrategicPlan target = findById(id);
        if (target.getStatus() != StrategicPlanStatus.ACTIVE && target.getStatus() != StrategicPlanStatus.SUPERSEDED)
            throw new BadRequestException("Somente um plano ATIVO ou SUBSTITUÍDO pode ser encerrado.");
        target.setStatus(StrategicPlanStatus.CLOSED);
        return plans.save(target);
    }

    public StrategicPlan rollover(UUID id) {
        StrategicPlan current = findById(id);
        int span = current.getEndYear() - current.getStartYear();
        StrategicPlan next = new StrategicPlan();
        next.setName("Plano Estratégico " + (current.getEndYear()) + "-" + (current.getEndYear() + span));
        next.setStartYear(current.getEndYear());
        next.setEndYear(current.getEndYear() + span);
        next.setHorizonYears(span + 1);
        next.setStatus(StrategicPlanStatus.DRAFT);
        next.setRevisionNumber(1);
        next.setDescription("Rollover de " + current.getName());
        StrategicPlan saved = plans.save(next);
        if (current.getStatus() == StrategicPlanStatus.ACTIVE) {
            current.setStatus(StrategicPlanStatus.SUPERSEDED);
            plans.save(current);
        }
        return saved;
    }

    public ConditionalApprovalResponse conditionalApprove(UUID planId, String actorId, String actorName) {
        return conditionalApprove(planId, actorId, actorName, true);
    }

    private ConditionalApprovalResponse conditionalApprove(UUID planId, String actorId, String actorName,
            boolean conditionalPlanApproval) {
        StrategicPlan plan = findById(planId);

        if (plan.getStatus() != StrategicPlanStatus.DRAFT) {
            throw new BadRequestException("Somente um plano em DRAFT pode ser aprovado condicionalmente.");
        }

        int convertedToProjects = 0;
        int convertedToPrograms = 0;
        int projectsCreated = 0;
        int programsCreated = 0;
        int ignoredDemands = 0;
        List<String> errors = new ArrayList<>();

        // Find planning cycles with APPROVED or CONDITIONALLY_APPROVED status
        List<DemandPlanningCycle> approvedCycles = planningCycles.findAllByStrategicPlan_IdAndStatusIn(
                planId, APPROVED_CYCLE_STATUSES);

        String approvalMode = conditionalPlanApproval ? "CONDITIONAL" : "FINAL";
        String approvalDescription = conditionalPlanApproval ? "condicional" : "definitiva";

        for (DemandPlanningCycle cycle : approvedCycles) {
            Demand demand = cycle.getDemand();
            // Skip already converted demands
            if (isAlreadyConverted(cycle)) {
                ignoredDemands++;
                continue;
            }

            try {
                // Build list of implementation directions
                // Include owner direction if it has IMPLEMANTATION type
                List<DemandParticipatingDirection> implementationDirections = new ArrayList<>();

                if (demand.getDirectionParticipationType() == DirectionParticipationType.IMPLEMANTATION) {
                    // Create a pseudo participating direction for the owner
                    DemandParticipatingDirection ownerDirection = new DemandParticipatingDirection();
                    ownerDirection.setDirectionName(demand.getDirectionName());
                    ownerDirection.setDirectionCode(demand.getDirectionCode());
                    ownerDirection.setAreaName(demand.getAreaName());
                    ownerDirection.setAreaCode(demand.getAreaCode());
                    ownerDirection.setParticipationType(DirectionParticipationType.IMPLEMANTATION);
                    implementationDirections.add(ownerDirection);
                }

                // Add participating directions with IMPLEMANTATION type
                implementationDirections.addAll(demand.getParticipatingDirections()
                        .stream()
                        .filter(pd -> pd.getParticipationType() == DirectionParticipationType.IMPLEMANTATION)
                        .collect(Collectors.toList()));

                // If no implementation directions, skip this demand
                if (implementationDirections.isEmpty()) {
                    ignoredDemands++;
                    continue;
                }

                if (implementationDirections.size() == 1) {
                    // Create only 1 Project
                    Project project = createProjectFromDemand(demand, cycle, implementationDirections.get(0), null, false);
                    project.setCreatedFromConditionalPlanApproval(conditionalPlanApproval);
                    project.setCreatedFromDemandBacklog(false);
                    projects.saveFromDemand(project, demand, cycle, null, null, null, null, null, actorId);
                    projectsCreated++;
                    convertedToProjects++;

                    convertCycleToProject(cycle, project, actorId);

                    // Log history
                    historyService.log(demand, "DEMAND_CONVERTED_TO_PROJECT_BY_" + approvalMode + "_PLAN_APPROVAL",
                            IN_PLAN.name(), IN_PLAN.name(),
                            "Demanda convertida em Projeto pela aprovação " + approvalDescription
                                    + " do Plano Estratégico",
                            actorId, actorName, Map.of("projectId", project.getId().toString()));
                } else {
                    // Create 1 Program with 1 Project per implementation direction
                    Program program = createProgramFromDemand(demand, cycle);
                    program.setCreatedFromConditionalPlanApproval(conditionalPlanApproval);
                    programs.save(program);
                    programsCreated++;
                    convertedToPrograms++;

                    for (DemandParticipatingDirection direction : implementationDirections) {
                        Project project = createProjectFromDemand(demand, cycle, direction, program, true);
                        project.setCreatedFromConditionalPlanApproval(conditionalPlanApproval);
                        project.setCreatedFromDemandBacklog(false);
                        projects.saveFromDemand(project, demand, cycle, null, null, null, null, null, actorId);
                        projectsCreated++;
                    }

                    convertCycleToProgram(cycle, program, actorId);

                    // Log history
                    historyService.log(demand, "DEMAND_CONVERTED_TO_PROGRAM_BY_" + approvalMode + "_PLAN_APPROVAL",
                            IN_PLAN.name(), IN_PLAN.name(),
                            "Demanda convertida em Programa pela aprovação " + approvalDescription
                                    + " do Plano Estratégico",
                            actorId, actorName, Map.of("programId", program.getId().toString(),
                                    "projectsCreated", String.valueOf(implementationDirections.size())));
                }
            } catch (Exception e) {
                errors.add("Demanda " + demand.getCode() + ": " + e.getMessage());
            }
        }

        // Update plan status
        plan.setStatus(StrategicPlanStatus.CONDITIONALLY_APPROVED);
        plans.save(plan);

        return new ConditionalApprovalResponse(
                planId,
                StrategicPlanStatus.CONDITIONALLY_APPROVED,
                convertedToProjects,
                convertedToPrograms,
                projectsCreated,
                programsCreated,
                0, // movedToBacklog is 0 for conditional approval
                ignoredDemands,
                errors);
    }

    public FinalApprovalResponse approve(UUID planId, String actorId, String actorName) {
        StrategicPlan plan = findById(planId);

        if (plan.getStatus() != StrategicPlanStatus.DRAFT
                && plan.getStatus() != StrategicPlanStatus.CONDITIONALLY_APPROVED) {
            throw new BadRequestException(
                    "Somente um plano DRAFT ou CONDITIONALLY_APPROVED pode ser aprovado definitivamente.");
        }

        int alreadyConverted = (int) planningCycles.findAllByStrategicPlan_Id(planId).stream()
                .filter(this::isAlreadyConverted)
                .count();
        int convertedToProjects = 0;
        int convertedToPrograms = 0;
        int projectsCreated = 0;
        int programsCreated = 0;
        int movedToBacklog = 0;
        int backlogProjectsCreated = 0;
        List<String> errors = new ArrayList<>();

        if (plan.getStatus() == StrategicPlanStatus.DRAFT) {
            ConditionalApprovalResponse conversion = conditionalApprove(planId, actorId, actorName, false);
            convertedToProjects = conversion.convertedToProjects();
            convertedToPrograms = conversion.convertedToPrograms();
            projectsCreated = conversion.projectsCreated();
            programsCreated = conversion.programsCreated();
            errors.addAll(conversion.errors());
            plan = findById(planId);
        }

        // Create backlog projects for cycles not selected by the committee.
        List<DemandPlanningCycle> cyclesToBacklog = planningCycles.findAllByStrategicPlan_IdAndStatusIn(
                planId, BACKLOG_CYCLE_STATUSES);

        for (DemandPlanningCycle cycle : cyclesToBacklog) {
            Demand demand = cycle.getDemand();
            if (isAlreadyConverted(cycle)) {
                continue;
            }
            try {
                DemandPlanningStatus previousStatus = cycle.getStatus();
                Project backlogProject = createBacklogProjectFromDemand(demand, cycle);
                Project savedProject = projects.saveFromDemand(
                        backlogProject, demand, cycle, null, null, null, null, null, actorId);

                if (previousStatus != BACKLOG) {
                    scoreLifecycle.applyStatusTransition(cycle, previousStatus, BACKLOG,
                            "Demanda não selecionada pelo cenário final do Plano Estratégico");
                    cycle.setStatus(BACKLOG);
                    movedToBacklog++;
                }
                convertCycleToProject(cycle, savedProject, actorId,
                        "Demanda convertida em projeto de backlog pela aprovação final do Plano Estratégico");
                backlogProjectsCreated++;

                historyService.log(demand, "DEMAND_CONVERTED_TO_PROJECT_BACKLOG_BY_FINAL_PLAN_APPROVAL",
                        previousStatus.name(), BACKLOG.name(),
                        "Demanda enviada para o backlog de projetos na aprovação final do Plano Estratégico.",
                        actorId, actorName, Map.of("projectId", savedProject.getId().toString(),
                                "projectStatus", ProjectStatus.BACKLOG.name()));
            } catch (Exception e) {
                errors.add("Demanda " + demand.getCode() + ": " + e.getMessage());
            }
        }

        plans.findFirstByStatus(StrategicPlanStatus.ACTIVE).ifPresent(current -> {
            if (!current.getId().equals(planId)) {
                current.setStatus(StrategicPlanStatus.SUPERSEDED);
                plans.save(current);
            }
        });

        // Final approval activates the plan immediately.
        plan.setStatus(StrategicPlanStatus.ACTIVE);
        plan.setApprovalDate(LocalDate.now());
        plan.setApprovedBy(actorName);
        plans.save(plan);

        return new FinalApprovalResponse(
                planId,
                StrategicPlanStatus.ACTIVE,
                convertedToProjects,
                convertedToPrograms,
                projectsCreated,
                programsCreated,
                alreadyConverted,
                movedToBacklog,
                backlogProjectsCreated,
                errors);
    }

    private boolean isAlreadyConverted(DemandPlanningCycle cycle) {
        return cycle.getConvertedProject() != null || cycle.getConvertedProgram() != null;
    }

    private void convertCycleToProject(DemandPlanningCycle cycle, Project project, String actorId) {
        convertCycleToProject(cycle, project, actorId, "Demanda convertida em projeto");
    }

    /**
     * A conversão não altera o status do ciclo — esse é decidido pelo cenário final. O que marca
     * a Demanda como convertida é a ligação ao projeto/programa gerado.
     */
    private void convertCycleToProject(DemandPlanningCycle cycle, Project project, String actorId, String reason) {
        cycle.setConvertedProject(project);
        cycle.setConvertedAt(Instant.now());
        cycle.setConvertedBy(actorId);
        planningCycles.save(cycle);
    }

    private void convertCycleToProgram(DemandPlanningCycle cycle, Program program, String actorId) {
        cycle.setConvertedProgram(program);
        cycle.setConvertedAt(Instant.now());
        cycle.setConvertedBy(actorId);
        planningCycles.save(cycle);
    }

    private Program createProgramFromDemand(Demand demand, DemandPlanningCycle cycle) {
        Program program = new Program();
        program.setName(demand.getTitle());
        program.setDescription(demand.getSummary());
        program.setStrategicPlan(cycle.getStrategicPlan());
        program.setOperationalPlan(cycle.getOperationalPlan());
        program.setStrategicPillar(cycle.getStrategicPillar());
        program.setStrategicObjective(cycle.getStrategicObjective());
        program.setDomain(demand.getDomain());
        program.setDirectionName(demand.getDirectionName());
        program.setDirectionCode(demand.getDirectionCode());
        program.setAreaName(demand.getAreaName());
        program.setAreaCode(demand.getAreaCode());
        program.setEstimatedBudget(demand.getEstimatedBudget());
        program.setSourceDemandId(demand.getId());
        program.setSourceDemandPortfolioRank(cycle.getPortfolioRank());
        program.setStatus(ProgramStatus.PLANNED);
        return program;
    }

    private Project createProjectFromDemand(Demand demand, DemandPlanningCycle cycle, DemandParticipatingDirection direction,
            Program program, boolean isMultipleDirections) {
        Project project = new Project();
        // If multiple directions (in a program), add direction code to name
        String projectName = demand.getTitle();
        if (isMultipleDirections && direction.getDirectionCode() != null) {
            projectName = direction.getDirectionCode() + " - " + projectName;
        }
        project.setName(projectName);
        project.setDescription(demand.getSummary());
        project.setStrategicPlan(cycle.getStrategicPlan());
        project.setOperationalPlan(cycle.getOperationalPlan());
        project.setStrategicPillar(cycle.getStrategicPillar());
        project.setStrategicObjective(cycle.getStrategicObjective());
        project.setDomain(demand.getDomain());
        project.setEstimatedBudget(demand.getEstimatedBudget());
        project.setDirectionName(direction.getDirectionName());
        project.setDirectionCode(direction.getDirectionCode());
        project.setAreaName(direction.getAreaName());
        project.setAreaCode(direction.getAreaCode());
        project.setExpectedBenefit(demand.getDesiredOutcome());
        project.setExpectedBenefits(demand.getDesiredOutcome());
        project.setSourceDemandPortfolioRank(cycle.getPortfolioRank());
        project.setExecutionRank(cycle.getPortfolioRank());
        project.setStatus(ProjectStatus.PLANNED);
        project.setProgram(program);
        return project;
    }

    private Project createBacklogProjectFromDemand(Demand demand, DemandPlanningCycle cycle) {
        Project project = new Project();
        DemandParticipatingDirection fallbackDirection = demand.getParticipatingDirections().stream()
                .filter(direction -> direction.getParticipationType() == DirectionParticipationType.IMPLEMANTATION)
                .findFirst()
                .orElse(null);
        String directionName = demand.getDirectionName();
        String directionCode = demand.getDirectionCode();
        String areaName = demand.getAreaName();
        String areaCode = demand.getAreaCode();
        if ((directionName == null || directionName.isBlank()) && fallbackDirection != null) {
            directionName = fallbackDirection.getDirectionName();
            directionCode = fallbackDirection.getDirectionCode();
            areaName = fallbackDirection.getAreaName();
            areaCode = fallbackDirection.getAreaCode();
        }
        project.setName(demand.getTitle());
        project.setDescription(demand.getSummary());
        project.setStrategicPlan(cycle.getStrategicPlan());
        project.setOperationalPlan(cycle.getOperationalPlan());
        project.setStrategicPillar(cycle.getStrategicPillar());
        project.setStrategicObjective(cycle.getStrategicObjective());
        project.setDomain(demand.getDomain());
        project.setBusinessArea(demand.getAreaName());
        project.setDirectionName(directionName);
        project.setDirectionCode(directionCode);
        project.setAreaName(areaName);
        project.setAreaCode(areaCode);
        project.setImpactedSystem(demand.getImpactedSystem());
        project.setExpectedBenefit(demand.getDesiredOutcome());
        project.setExpectedBenefits(demand.getDesiredOutcome());
        project.setBudgetLine(demand.getBudgetLine() == null ? null : demand.getBudgetLine().getCode());
        project.setBudget(demand.getEstimatedBudget());
        project.setEstimatedBudget(demand.getEstimatedBudget());
        project.setSourceDemandPortfolioRank(cycle.getPortfolioRank());
        project.setExecutionRank(cycle.getPortfolioRank());
        project.setCreatedFromConditionalPlanApproval(false);
        project.setCreatedFromDemandBacklog(true);
        project.setStatus(ProjectStatus.BACKLOG);
        return project;
    }
}
