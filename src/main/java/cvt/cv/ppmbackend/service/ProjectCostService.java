package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectCostDtos.Financials;
import cvt.cv.ppmbackend.dto.ProjectCostDtos.Request;
import cvt.cv.ppmbackend.dto.ProjectCostDtos.Response;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectCost;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.enums.ProjectCostSource;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import cvt.cv.ppmbackend.repository.ProjectCostRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ProjectCostService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ProjectCostRepository costs;
    private final LookupValueRepository lookups;
    private final ProjectRepository projects;
    private final ProjectGuard guard;
    private final ProjectEventService events;

    public ProjectCostService(ProjectCostRepository costs, LookupValueRepository lookups, ProjectRepository projects,
            ProjectGuard guard, ProjectEventService events) {
        this.costs = costs;
        this.lookups = lookups;
        this.projects = projects;
        this.guard = guard;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID projectId) {
        Project project = guard.require(projectId);
        requireVisible(project);
        return costs.findByProjectIdOrderByMonthAscCreatedAtAsc(projectId).stream()
                .map(ProjectCostService::toResponse).toList();
    }

    public Response create(UUID projectId, Request request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectCost cost = new ProjectCost();
        cost.setProject(project);
        cost.setCreatedBy(actor(actor));
        apply(cost, request, project);
        costs.save(cost);
        refreshConsumed(project, actor);
        events.log(project, ProjectEventType.COST_CHANGED,
                "Custo de " + cost.getMonth().getYear() + "-" + String.format("%02d", cost.getMonth().getMonthValue())
                        + " registado",
                actor);
        return toResponse(cost);
    }

    public Response update(UUID projectId, UUID costId, Request request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectCost cost = require(projectId, costId);
        requireManual(cost);
        apply(cost, request, project);
        cost.setUpdatedBy(actor(actor));
        costs.save(cost);
        refreshConsumed(project, actor);
        events.log(project, ProjectEventType.COST_CHANGED, "Custo alterado", actor);
        return toResponse(cost);
    }

    public void delete(UUID projectId, UUID costId, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectCost cost = require(projectId, costId);
        requireManual(cost);
        costs.delete(cost);
        costs.flush();
        refreshConsumed(project, actor);
        events.log(project, ProjectEventType.COST_CHANGED, "Custo removido", actor);
    }

    @Transactional(readOnly = true)
    public Financials financials(UUID projectId) {
        Project project = guard.require(projectId);
        requireVisible(project);
        List<ProjectCost> rows = costs.findByProjectIdOrderByMonthAscCreatedAtAsc(projectId);
        BigDecimal planned = sum(rows.stream().map(ProjectCost::getPlanned).toList());
        BigDecimal actual = sum(rows.stream().map(ProjectCost::getActual).toList());
        LocalDate lastActual = rows.stream().filter(c -> c.getActual() != null).map(ProjectCost::getMonth)
                .max(LocalDate::compareTo).orElse(null);
        BigDecimal futurePlanned = lastActual == null ? planned : sum(rows.stream()
                .filter(c -> c.getMonth().isAfter(lastActual)).map(ProjectCost::getPlanned).toList());
        BigDecimal eac = actual.add(futurePlanned);
        BigDecimal budget = project.getBudget() == null ? ZERO : project.getBudget();
        return new Financials(planned, actual, eac, budget.subtract(eac),
                lastActual == null ? null : YearMonth.from(lastActual).toString());
    }

    private void apply(ProjectCost cost, Request r, Project project) {
        if (r == null || r.budgetLineId() == null) throw new BadRequestException("budgetLineId é obrigatório.");
        if (r.type() == null) throw new BadRequestException("type é obrigatório (CAPEX ou OPEX).");
        if (r.planned() == null || r.planned().signum() < 0 || negative(r.actual())) {
            throw new BadRequestException("Os valores planeado e real não podem ser negativos.");
        }
        LookupValue budgetLine = lookups.findById(r.budgetLineId())
                .orElseThrow(() -> new BadRequestException("Rubrica orçamental não encontrada."));
        if (!"BUDGET_LINE".equalsIgnoreCase(budgetLine.getCategory()) || !Boolean.TRUE.equals(budgetLine.getActive())) {
            throw new BadRequestException("A rubrica orçamental tem de estar ativa.");
        }
        LocalDate month = parseMonth(r.month());
        validateWindow(project, month);
        cost.setBudgetLine(budgetLine);
        cost.setMonth(month);
        cost.setType(r.type());
        cost.setPlanned(r.planned());
        cost.setActual(r.actual());
        cost.setDescription(trim(r.description()));
        if (cost.getId() == null) cost.setSource(r.source() == null ? ProjectCostSource.MANUAL : r.source());
    }

    private static LocalDate parseMonth(String value) {
        try {
            return YearMonth.parse(value).atDay(1);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("month deve ter o formato AAAA-MM.");
        }
    }

    private static void validateWindow(Project project, LocalDate month) {
        if (project.getExecution() == null) return;
        LocalDate start = project.getExecution().getPlannedStartDate();
        LocalDate end = project.getExecution().getPlannedEndDate();
        if (start != null && month.isBefore(YearMonth.from(start).minusMonths(1).atDay(1))) {
            throw new BadRequestException("O mês está antes do intervalo financeiro do projeto.");
        }
        if (end != null && month.isAfter(YearMonth.from(end).plusMonths(3).atDay(1))) {
            throw new BadRequestException("O mês está depois do intervalo financeiro do projeto.");
        }
    }

    private void refreshConsumed(Project project, String actor) {
        ProjectExecution execution = project.getExecution();
        if (execution == null) {
            execution = new ProjectExecution();
            project.attachExecution(execution);
        }
        execution.setConsumedBudget(costs.sumActualByProjectId(project.getId()));
        execution.setLastUpdatedAt(Instant.now());
        execution.setLastUpdatedBy(actor(actor));
        projects.save(project);
    }

    private ProjectCost require(UUID projectId, UUID costId) {
        return costs.findByIdAndProjectId(costId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Custo não encontrado neste projeto: " + costId));
    }

    private static void requireManual(ProjectCost cost) {
        if (cost.getSource() == ProjectCostSource.ERP) {
            throw new DomainException(HttpStatus.CONFLICT, "ERP_COST_IMMUTABLE",
                    "As linhas importadas do ERP não podem ser alteradas nem removidas.", Map.of());
        }
    }

    private void requireVisible(Project project) {
        if (!guard.canView(project)) throw new ResourceNotFoundException("Projeto não encontrado: " + project.getId());
    }

    private static Response toResponse(ProjectCost c) {
        return new Response(c.getId(), c.getProject().getId(), c.getBudgetLine().getId(), c.getBudgetLine().getLabel(),
                YearMonth.from(c.getMonth()).toString(), c.getType(), c.getPlanned(), c.getActual(),
                c.getDescription(), c.getSource(), c.getCreatedAt(), c.getCreatedBy(), c.getUpdatedAt(), c.getUpdatedBy());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(java.util.Objects::nonNull).reduce(ZERO, BigDecimal::add);
    }

    private static boolean negative(BigDecimal value) { return value != null && value.signum() < 0; }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String actor(String value) { return value == null || value.isBlank() ? "system" : value.trim(); }
}
