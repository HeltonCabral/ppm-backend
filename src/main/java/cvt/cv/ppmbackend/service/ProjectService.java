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

    public ProjectService(ProjectRepository projects, ProjectExecutionRankHistoryRepository rankHistory,
            LookupValueRepository lookups, SupplierRepository suppliers, ProgramRepository programs,
            StrategicPlanRepository strategicPlans, OperationalPlanRepository operationalPlans,
            StrategicPillarRepository strategicPillars, StrategicObjectiveRepository strategicObjectives) {
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
        return ProjectResponse.from(findById(id));
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
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("executionRank").ascending());
        var result = projects.findAll(spec, pageable);
        return new PageResponse<>(result.map(ProjectListItemResponse::from).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public ProjectResponse createExtraPlan(ProjectCreateExtraPlanRequest request, String actor) {
        validateDates(request.plannedStartDate(), request.plannedEndDate());
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
        return ProjectResponse.from(projects.save(project));
    }

    public ProjectResponse update(UUID id, ProjectUpdateRequest request, String actor) {
        Project p = findById(id);
        if (p.getOrigin() == ProjectOrigin.EXTRA_PLAN && !hasText(request.extraPlanJustification())) {
            throw new BadRequestException("extraPlanJustification é obrigatória para projeto EXTRA_PLAN");
        }
        if (p.getOrigin() != ProjectOrigin.DEMAND && !hasText(request.directionName())) {
            throw new BadRequestException("directionName é obrigatória para projeto não originado de demanda");
        }
        p.setName(request.name());
        p.setDescription(request.description());
        p.setProgram(entity(programs, request.programId(), "Programa"));
        p.setStrategicPlan(entity(strategicPlans, request.strategicPlanId(), "Plano estratégico"));
        p.setOperationalPlan(entity(operationalPlans, request.operationalPlanId(), "Plano operacional"));
        p.setStrategicPillar(entity(strategicPillars, request.strategicPillarId(), "Pilar estratégico"));
        p.setStrategicObjective(entity(strategicObjectives, request.strategicObjectiveId(), "Objetivo estratégico"));
        if (p.getOrigin() != ProjectOrigin.DEMAND) {
            p.setDirectionCode(trim(request.directionCode()));
            p.setDirectionName(request.directionName().trim());
            p.setAreaCode(trim(request.areaCode()));
            p.setAreaName(trim(request.areaName()));
        }
        p.setDomain(resolveDomain(request.domain()));
        p.setMainSupplier(entity(suppliers, request.supplierId(), "Fornecedor"));
        p.setImpactedSystem(request.impactedSystem());
        p.setExpectedBenefits(request.expectedBenefits());
        p.setBudget(request.budget());
        //p.setStatus(request.status());
        p.setProjectPhase(entity(lookups, request.projectPhaseId(), "Fase do projeto"));
        if (request.projectManager() != null) p.setProjectManager(request.projectManager().trim());
        p.setExtraPlanJustification(trim(request.extraPlanJustification()));
        p.setUpdatedBy(actor(actor));
        // Update execution data if provided
        ProjectExecution exec = p.getExecution();
        if (exec != null) {
            if (request.progress() != null) exec.setProgress(request.progress());
            if (request.consumedBudget() != null) exec.setConsumedBudget(request.consumedBudget());
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
        return ProjectResponse.from(projects.save(p));
    }

    public Project saveFromDemand(Project p, Demand demand, LocalDate plannedStart, LocalDate actualStart,
            LocalDate plannedEnd, LocalDate actualEnd, String delayReasons, String actor) {
        validateDates(plannedStart, plannedEnd);
        if (p.getCode() == null) p.setCode(nextCode());
        p.setOrigin(ProjectOrigin.DEMAND);
        p.setSourceDemand(demand);
        p.setStatus(ProjectStatus.PLANNED);
        p.setExecutionRank(demand.getPortfolioRank());
        p.setSourceDemandPortfolioRank(demand.getPortfolioRank());
        if (p.getBudget() == null) p.setBudget(demand.getEstimatedBudget());
        if (!hasText(p.getExpectedBenefits())) p.setExpectedBenefits(demand.getExpectedBenefit());
        if (p.getStrategicPlan() == null) p.setStrategicPlan(demand.getStrategicPlan());
        if (p.getOperationalPlan() == null) p.setOperationalPlan(demand.getOperationalPlan());
        if (p.getStrategicPillar() == null) p.setStrategicPillar(demand.getStrategicPillar());
        if (p.getStrategicObjective() == null) p.setStrategicObjective(demand.getStrategicObjective());
        if (!hasText(p.getDirectionName())) {
            p.setDirectionName(firstNonBlank(demand.getDirectionName(), demand.getDirectionCode()));
            p.setDirectionCode(hasText(demand.getDirectionName()) ? demand.getDirectionCode() : null);
        }
        if (!hasText(p.getAreaName())) {
            p.setAreaName(firstNonBlank(demand.getAreaName(), demand.getAreaCode()));
            p.setAreaCode(hasText(demand.getAreaName()) ? demand.getAreaCode() : null);
        }
        if (!hasText(p.getDirectionName())) throw new BadRequestException("directionName é obrigatória no projeto");
        p.setCreatedBy(actor(actor));
        p.setUpdatedBy(actor(actor));
        p.attachExecution(newExecution(plannedStart, actualStart, plannedEnd, actualEnd, delayReasons, actor));
        return projects.save(p);
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
        p.setStatus(status);
        p.setUpdatedBy(actor(actor));
        return ProjectResponse.from(projects.save(p));
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
        return ProjectResponse.from(projects.save(project));
    }

    public ProjectResponse complete(UUID id, String actor) {
        Project project = findById(id);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new BadRequestException("Apenas projetos em progresso podem ser concluídos");
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

        project.setStatus(ProjectStatus.COMPLETED);
        project.setUpdatedBy(updatedBy);
        return ProjectResponse.from(projects.save(project));
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

    private static String actor(String actor) { return hasText(actor) ? actor.trim() : "system"; }
    private static String trim(String value) { return hasText(value) ? value.trim() : null; }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String firstNonBlank(String... values) {
        for (String value : values) if (hasText(value)) return value.trim();
        return null;
    }
}
