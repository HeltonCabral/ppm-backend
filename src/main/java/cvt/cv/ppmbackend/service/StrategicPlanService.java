package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.ConditionalApprovalResponse;
import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.FinalApprovalResponse;
import cvt.cv.ppmbackend.dto.StrategicPlanCreateRequest;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class StrategicPlanService extends AbstractCrudService<StrategicPlan, StrategicPlanCreateRequest> {
    private final StrategicPlanRepository plans;
    private final DemandRepository demands;
    private final ProgramRepository programs;
    private final ProjectRepository projects;
    private final DemandHistoryService historyService;

    private static final String STATUS_CONVERTED_TO_PROJECT = "CONVERTED_TO_PROJECT";
    private static final String STATUS_CONVERTED_TO_PROGRAM = "CONVERTED_TO_PROGRAM";
    private static final String STATUS_BACKLOG = "BACKLOG";
    private static final String STATUS_UNDER_ANALYSIS = "UNDER_ANALYSIS";
    private static final String STATUS_IN_PRIORITIZATION = "IN_PRIORITIZATION";
    private static final String STATUS_PRIORITIZED = "PRIORITIZED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_CONDITIONALLY_APPROVED = "CONDITIONALLY_APPROVED";

    public StrategicPlanService(StrategicPlanRepository r, DemandRepository demands,
            ProgramRepository programs, ProjectRepository projects, DemandHistoryService historyService) {
        super(r, "Plano estratégico");
        this.plans = r;
        this.demands = demands;
        this.programs = programs;
        this.projects = projects;
        this.historyService = historyService;
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
            e.setRevision(1);
        }
        return e;
    }

    @Transactional(readOnly = true)
    public List<StrategicPlan> findAllOrdered() {
        return plans.findAllByOrderByStartYearDesc();
    }

    public StrategicPlan activate(UUID id) {
        StrategicPlan target = findById(id);
        if (target.getStatus() == StrategicPlanStatus.ACTIVE)
            return target;
        plans.findFirstByStatus(StrategicPlanStatus.ACTIVE).ifPresent(current -> {
            current.setStatus(StrategicPlanStatus.REPLACED);
            plans.save(current);
        });
        target.setStatus(StrategicPlanStatus.ACTIVE);
        return plans.save(target);
    }

    public StrategicPlan rollover(UUID id) {
        StrategicPlan current = findById(id);
        int span = current.getEndYear() - current.getStartYear();
        StrategicPlan next = new StrategicPlan();
        next.setName("Plano Estratégico " + (current.getEndYear()) + "-" + (current.getEndYear() + span));
        next.setStartYear(current.getEndYear());
        next.setEndYear(current.getEndYear() + span);
        next.setStatus(StrategicPlanStatus.DRAFT);
        next.setRevision(1);
        next.setDescription("Rollover de " + current.getName());
        StrategicPlan saved = plans.save(next);
        if (current.getStatus() == StrategicPlanStatus.ACTIVE) {
            current.setStatus(StrategicPlanStatus.REPLACED);
            plans.save(current);
        }
        return saved;
    }

    public ConditionalApprovalResponse conditionalApproval(UUID planId, String actorId, String actorName) {
        StrategicPlan plan = findById(planId);

        // Validation: cannot conditionally approve if already finally approved
        if (plan.getStatus() == StrategicPlanStatus.APPROVED) {
            throw new BadRequestException("Plano já aprovado definitivamente. Não é possível aprovar condicionalmente.");
        }

        int convertedToProjects = 0;
        int convertedToPrograms = 0;
        int projectsCreated = 0;
        int programsCreated = 0;
        int ignoredDemands = 0;
        List<String> errors = new ArrayList<>();

        // Find demands with APPROVED or CONDITIONALLY_APPROVED status
        List<Demand> approvedDemands = demands.findByStrategicPlanIdAndStatusesOrdered(
                planId, List.of(STATUS_APPROVED, STATUS_CONDITIONALLY_APPROVED));

        System.out.println("Found " + approvedDemands.size() + " approved demands for plan " + planId);

        Instant now = Instant.now();

        for (Demand demand : approvedDemands) {
            // Skip already converted demands
            if (isAlreadyConverted(demand)) {
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
                    Project project = createProjectFromDemand(demand, implementationDirections.get(0), null, false, now);
                    projects.save(project);
                    projectsCreated++;
                    convertedToProjects++;

                    // Update demand - set converted reference but keep original status
                    demand.setConvertedProject(project);
                    demand.setConvertedToProject(true);
                    demand.setConvertedAt(now);
                    demand.setConvertedBy(actorId);
                    demands.save(demand);

                    // Log history
                    historyService.log(demand, "DEMAND_CONVERTED_TO_PROJECT_BY_CONDITIONAL_PLAN_APPROVAL",
                            demand.getStatus(), demand.getStatus(),
                            "Demanda convertida em Projeto pela aprovação condicional do Plano Estratégico",
                            actorId, actorName, Map.of("projectId", project.getId().toString()));
                } else {
                    // Create 1 Program with 1 Project per implementation direction
                    Program program = createProgramFromDemand(demand, now);
                    programs.save(program);
                    programsCreated++;
                    convertedToPrograms++;

                    for (DemandParticipatingDirection direction : implementationDirections) {
                        Project project = createProjectFromDemand(demand, direction, program, true, now);
                        projects.save(project);
                        projectsCreated++;
                    }

                    // Update demand - set converted reference but keep original status
                    demand.setConvertedProgram(program);
                    demand.setConvertedToProgram(true);
                    demand.setConvertedAt(now);
                    demand.setConvertedBy(actorId);
                    demands.save(demand);

                    // Log history
                    historyService.log(demand, "DEMAND_CONVERTED_TO_PROGRAM_BY_CONDITIONAL_PLAN_APPROVAL",
                            demand.getStatus(), demand.getStatus(),
                            "Demanda convertida em Programa pela aprovação condicional do Plano Estratégico",
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

    public FinalApprovalResponse finalApproval(UUID planId, String actorId, String actorName) {
        StrategicPlan plan = findById(planId);

        int alreadyConverted = 0;
        int movedToBacklog = 0;
        List<String> errors = new ArrayList<>();

        Instant now = Instant.now();

        // Count already converted demands
        List<Demand> allPlanDemands = demands.findByStrategicPlanIdAndStatusesOrdered(
                planId, List.of(STATUS_APPROVED, STATUS_CONDITIONALLY_APPROVED));
        
        for (Demand demand : allPlanDemands) {
            if (isAlreadyConverted(demand)) {
                alreadyConverted++;
            }
        }

        // Find demands still in analysis/prioritization states and move to BACKLOG
        List<Demand> demandsToBacklog = demands.findByStrategicPlanIdAndStatusIn(
                planId, List.of(STATUS_UNDER_ANALYSIS, STATUS_IN_PRIORITIZATION, STATUS_PRIORITIZED));

        for (Demand demand : demandsToBacklog) {
            try {
                String previousStatus = demand.getStatus();
                demand.setStatus(STATUS_BACKLOG);
                demands.save(demand);
                movedToBacklog++;

                // Log history
                historyService.log(demand, "DEMAND_MOVED_TO_BACKLOG_BY_FINAL_PLAN_APPROVAL",
                        previousStatus, STATUS_BACKLOG,
                        "Demanda movida automaticamente para Backlog na aprovação final do Plano Estratégico.",
                        actorId, actorName, null);
            } catch (Exception e) {
                errors.add("Demanda " + demand.getCode() + ": " + e.getMessage());
            }
        }

        // Update plan status
        plan.setStatus(StrategicPlanStatus.APPROVED);
        plan.setApprovalDate(LocalDate.now());
        plan.setApprovedBy(actorName);
        plans.save(plan);

        return new FinalApprovalResponse(
                planId,
                StrategicPlanStatus.APPROVED,
                alreadyConverted,
                movedToBacklog,
                errors);
    }

    private boolean isAlreadyConverted(Demand demand) {
        return demand.isConvertedToProject() || demand.isConvertedToProgram();
    }

    private Program createProgramFromDemand(Demand demand, Instant now) {
        Program program = new Program();
        program.setName(demand.getTitle());
        program.setDescription(demand.getDescription());
        program.setStrategicPlan(demand.getStrategicPlan());
        program.setOperationalPlan(demand.getOperationalPlan());
        program.setStrategicPillar(demand.getStrategicPillar());
        program.setStrategicObjective(demand.getStrategicObjective());
        program.setDomain(demand.getDomain());
        program.setDirectionName(demand.getDirectionName());
        program.setDirectionCode(demand.getDirectionCode());
        program.setAreaName(demand.getAreaName());
        program.setAreaCode(demand.getAreaCode());
        program.setEstimatedBudget(demand.getEstimatedBudget());
        program.setSourceDemandId(demand.getId());
        program.setSourceDemandPortfolioRank(demand.getPortfolioRank());
        program.setCreatedFromConditionalPlanApproval(true);
        program.setStatus(ProgramStatus.PLANNED);
        return program;
    }

    private Project createProjectFromDemand(Demand demand, DemandParticipatingDirection direction, Program program, boolean isMultipleDirections, Instant now) {
        Project project = new Project();
        // If multiple directions (in a program), add direction code to name
        String projectName = demand.getTitle();
        if (isMultipleDirections && direction.getDirectionCode() != null) {
            projectName = direction.getDirectionCode() + " - " + projectName;
        }
        project.setName(projectName);
        project.setDescription(demand.getDescription());
        project.setStrategicPlan(demand.getStrategicPlan());
        project.setOperationalPlan(demand.getOperationalPlan());
        project.setStrategicPillar(demand.getStrategicPillar());
        project.setStrategicObjective(demand.getStrategicObjective());
        project.setDomain(demand.getDomain());
        project.setProjectType(demand.getType());
        project.setEstimatedBudget(demand.getEstimatedBudget());
        project.setDesiredDate(demand.getDesiredDate());
        project.setDirectionName(direction.getDirectionName());
        project.setDirectionCode(direction.getDirectionCode());
        project.setAreaName(direction.getAreaName());
        project.setAreaCode(direction.getAreaCode());
        project.setExpectedImpact(demand.getExpectedImpact());
        project.setExpectedBenefit(demand.getExpectedBenefit());
        project.setSourceDemandId(demand.getId());
        project.setSourceDemandPortfolioRank(demand.getPortfolioRank());
        project.setPortfolioRank(demand.getPortfolioRank());
        project.setCreatedFromConditionalPlanApproval(true);
        project.setStatus(ProjectStatus.PLANNED);
        project.setProgram(program);
        return project;
    }
}
