package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.OperationalPlanCreateRequest;
import cvt.cv.ppmbackend.dto.OperationalPlanSummary;
import cvt.cv.ppmbackend.entity.OperationalPlan;
import cvt.cv.ppmbackend.entity.OperationalPlanBaseline;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.OperationalPlanStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.OperationalPlanBaselineRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class OperationalPlanService extends AbstractCrudService<OperationalPlan, OperationalPlanCreateRequest> {
    private final OperationalPlanRepository plans;
    private final OperationalPlanBaselineRepository baselines;
    private final StrategicPlanService strategicPlans;
    private final ProjectService projects;

    public OperationalPlanService(OperationalPlanRepository r, OperationalPlanBaselineRepository b,
            StrategicPlanService sp, ProjectService p) {
        super(r, "Plano operacional");
        plans = r;
        baselines = b;
        strategicPlans = sp;
        projects = p;
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
