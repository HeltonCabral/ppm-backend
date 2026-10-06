package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.OperationalPlanCreateRequest;
import cvt.cv.ppmbackend.dto.OperationalPlanOverviewDtos.Metrics;
import cvt.cv.ppmbackend.dto.OperationalPlanOverviewDtos.NextOperationalCycle;
import cvt.cv.ppmbackend.dto.OperationalPlanOverviewDtos.OperationalPlanHistoryItem;
import cvt.cv.ppmbackend.dto.OperationalPlanOverviewDtos.OperationalPlanRef;
import cvt.cv.ppmbackend.dto.OperationalPlanOverviewDtos.Overview;
import cvt.cv.ppmbackend.dto.OperationalPlanSummary;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.OperationalPlan;
import cvt.cv.ppmbackend.entity.OperationalPlanBaseline;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.OperationalPlanStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanBaselineRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class OperationalPlanService extends AbstractCrudService<OperationalPlan, OperationalPlanCreateRequest> {
    private final OperationalPlanRepository plans;
    private final OperationalPlanBaselineRepository baselines;
    private final StrategicPlanService strategicPlans;
    private final ProjectService projects;
    private final DemandPlanningCycleRepository planningCycles;

    public OperationalPlanService(OperationalPlanRepository r, OperationalPlanBaselineRepository b,
            StrategicPlanService sp, ProjectService p, DemandPlanningCycleRepository planningCycles) {
        super(r, "Plano operacional");
        plans = r;
        baselines = b;
        strategicPlans = sp;
        projects = p;
        this.planningCycles = planningCycles;
    }

    protected OperationalPlan newEntity() {
        return new OperationalPlan();
    }

    protected OperationalPlan apply(OperationalPlanCreateRequest r, OperationalPlan e) {
        StrategicPlan sp = strategicPlans.findById(r.strategicPlanId());
        if (r.fiscalYear() < sp.getStartYear() || r.fiscalYear() > sp.getEndYear())
            throw new BadRequestException("fiscalYear deve estar entre " + sp.getStartYear() + " e " + sp.getEndYear());
        plans.findByStrategicPlan_IdAndFiscalYear(sp.getId(), r.fiscalYear()).ifPresent(existing -> {
            if (e.getId() == null || !existing.getId().equals(e.getId()))
                throw new BadRequestException(
                        "Já existe plano operacional para o ano " + r.fiscalYear() + " neste plano estratégico");
        });
        e.setName(r.name());
        e.setFiscalYear(r.fiscalYear());
        e.setStrategicPlan(sp);
        e.setApprovedBudget(r.approvedBudget());
        e.setDescription(r.description());
        if (e.getStatus() == null)
            e.setStatus(OperationalPlanStatus.DRAFT);
        if (e.getVersion() == null)
            e.setVersion(1);
        recomputeTotalBudget(e);
        return e;
    }

    private void recomputeTotalBudget(OperationalPlan plan) {
        if (plan.getId() == null) {
            plan.setTotalBudget(BigDecimal.ZERO);
            return;
        }
        BigDecimal total = projects.findByOperationalPlan(plan.getId()).stream()
                .map(Project::getBudget)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        plan.setTotalBudget(total);
    }

    @Transactional(readOnly = true)
    public List<OperationalPlan> search(UUID strategicPlanId, Integer fiscalYear, OperationalPlanStatus status) {
        if (strategicPlanId != null)
            return plans.findByStrategicPlan_Id(strategicPlanId);
        if (fiscalYear != null)
            return plans.findByFiscalYear(fiscalYear);
        if (status != null)
            return plans.findByStatus(status);
        return plans.findAll();
    }

    public OperationalPlan approve(UUID id) {
        OperationalPlan plan = findById(id);
        if (plan.getStatus() != OperationalPlanStatus.DRAFT)
            throw new BadRequestException("Só é possível aprovar um plano em Draft");
        plan.setStatus(OperationalPlanStatus.APROVADO);
        plan.setVersion(plan.getVersion() + 1);
        plan.setApprovedAt(Instant.now());
        recomputeTotalBudget(plan);
        OperationalPlan saved = plans.save(plan);
        snapshotBaseline(saved);
        return saved;
    }

    public OperationalPlan close(UUID id) {
        OperationalPlan plan = findById(id);
        if (plan.getStatus() != OperationalPlanStatus.APROVADO && plan.getStatus() != OperationalPlanStatus.EM_EXECUCAO)
            throw new BadRequestException("Só é possível fechar um plano Aprovado ou Em Execução");
        plan.setStatus(OperationalPlanStatus.FECHADO);
        plan.setClosedAt(Instant.now());
        return plans.save(plan);
    }

    private void snapshotBaseline(OperationalPlan plan) {
        List<Project> projs = projects.findByOperationalPlan(plan.getId());
        for (Project p : projs) {
            OperationalPlanBaseline b = new OperationalPlanBaseline();
            b.setOperationalPlan(plan);
            b.setVersion(plan.getVersion());
            b.setProjectId(p.getId());
            b.setName(p.getName());
            b.setBudget(p.getBudget());
            ProjectExecution e = p.getExecution();
            b.setStartDate(e == null ? null
                    : e.getActualStartDate() != null ? e.getActualStartDate() : e.getPlannedStartDate());
            b.setEndDate(e == null ? null
                    : e.getActualEndDate() != null ? e.getActualEndDate() : e.getPlannedEndDate());
            baselines.save(b);
        }
    }

    @Transactional(readOnly = true)
    public List<OperationalPlanBaseline> baselinesOf(UUID id) {
        findById(id);
        return baselines.findByOperationalPlan_IdOrderByCapturedAtDesc(id);
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Optional<StrategicPlan> activeStrategicPlan = strategicPlans.findActivePlan();
        Optional<StrategicPlan> planningStrategicPlan = strategicPlans.findCurrentPlanningPlan();

        Optional<OperationalPlan> activePlan = activeStrategicPlan
                .flatMap(sp -> currentOperationalPlan(plans.findByStrategicPlan_Id(sp.getId())));
        Optional<OperationalPlan> planningPlan = planningStrategicPlan
                .flatMap(sp -> currentOperationalPlan(plans.findByStrategicPlan_Id(sp.getId())));

        OperationalPlanRef activeRef = activePlan.map(this::toRef).orElse(null);
        OperationalPlanRef planningRef = planningPlan.map(this::toRef).orElse(null);

        Metrics metrics = planningPlan.map(p -> progressOf(p.getId()))
                .orElse(new Metrics(0, 0, 0, 0, 0, 0, 0));

        int nextFiscalYear = planningPlan.map(OperationalPlan::getFiscalYear)
                .or(() -> activePlan.map(p -> p.getFiscalYear() + 1))
                .orElseGet(() -> LocalDate.now().getYear() + 1);
        boolean canStart = planningStrategicPlan
                .map(sp -> plans.findByStrategicPlan_IdAndFiscalYear(sp.getId(), nextFiscalYear).isEmpty())
                .orElse(activeStrategicPlan
                        .map(sp -> plans.findByStrategicPlan_IdAndFiscalYear(sp.getId(), nextFiscalYear).isEmpty())
                        .orElse(true));
        NextOperationalCycle nextOperationalCycle = new NextOperationalCycle(nextFiscalYear, canStart);

        List<OperationalPlanHistoryItem> history = plans.findAll().stream()
                .sorted(Comparator.comparing(OperationalPlan::getFiscalYear).reversed())
                .map(this::toHistoryItem)
                .toList();

        return new Overview(activeRef, planningRef, metrics, nextOperationalCycle, history);
    }

    private Optional<OperationalPlan> currentOperationalPlan(List<OperationalPlan> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int currentYear = LocalDate.now().getYear();
        return candidates.stream()
                .filter(p -> p.getFiscalYear() != null && p.getFiscalYear() <= currentYear)
                .max(Comparator.comparing(OperationalPlan::getFiscalYear))
                .or(() -> candidates.stream().min(Comparator.comparing(OperationalPlan::getFiscalYear)));
    }

    private OperationalPlanRef toRef(OperationalPlan p) {
        StrategicPlan sp = p.getStrategicPlan();
        return new OperationalPlanRef(p.getId(), p.getName(), p.getFiscalYear(), p.getStatus(),
                sp != null ? sp.getId() : null, sp != null ? sp.getName() : null,
                p.getApprovedBudget(), p.getTotalBudget());
    }

    private OperationalPlanHistoryItem toHistoryItem(OperationalPlan p) {
        StrategicPlan sp = p.getStrategicPlan();
        return new OperationalPlanHistoryItem(p.getId(), p.getName(), p.getFiscalYear(), p.getStatus(),
                sp != null ? sp.getId() : null, sp != null ? sp.getName() : null,
                projects.countByOperationalPlan(p.getId()));
    }

    private Metrics progressOf(UUID operationalPlanId) {
        List<DemandPlanningCycle> cycles = planningCycles.findAllByOperationalPlan_Id(operationalPlanId);
        long total = cycles.size();
        long approved = cycles.stream().filter(c -> c.getStatus() == DemandPlanningStatus.IN_PLAN).count();
        long conditionallyApproved = cycles.stream()
                .filter(c -> c.getStatus() == DemandPlanningStatus.IN_REVIEW).count();
        long backlog = cycles.stream().filter(c -> c.getStatus() == DemandPlanningStatus.BACKLOG).count();
        long deliberated = approved + backlog;
        long pending = total - deliberated;
        int deliberationPercentage = total == 0 ? 0 : (int) Math.round(deliberated * 100.0 / total);
        long projectsGenerated = projects.countByOperationalPlan(operationalPlanId);
        return new Metrics(total, approved, conditionallyApproved, backlog, pending, deliberationPercentage,
                projectsGenerated);
    }

    @Transactional(readOnly = true)
    public OperationalPlanSummary summary(UUID id) {
        findById(id);
        List<Project> projs = projects.findByOperationalPlan(id);
        BigDecimal budget = projs.stream().map(Project::getBudget).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long atRisk = projs.stream().filter(p -> p.getExecution() != null
                && p.getExecution().getRiskStatus() == ExecutiveStatus.RED).count();
        Map<String, Long> byStatus = projs.stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(p -> p.getStatus().name(), Collectors.counting()));
        return new OperationalPlanSummary(projs.size(), budget, BigDecimal.ZERO, atRisk, byStatus);
    }
}
