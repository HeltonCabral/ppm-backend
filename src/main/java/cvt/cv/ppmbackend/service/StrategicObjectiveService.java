package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicObjectiveCreateRequest;
import cvt.cv.ppmbackend.entity.Kpi;
import cvt.cv.ppmbackend.entity.KpiMeasurement;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import cvt.cv.ppmbackend.entity.StrategicObjectiveAnnualTarget;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StrategicObjectiveService
        extends AbstractCrudService<StrategicObjective, StrategicObjectiveCreateRequest> {
    private final StrategicObjectiveRepository objectives;
    private final StrategicElementService elements;
    private final StrategicPlanService strategicPlans;

    public StrategicObjectiveService(StrategicObjectiveRepository r, StrategicElementService e,
            StrategicPlanService sp) {
        super(r, "Objetivo estratégico");
        objectives = r;
        elements = e;
        strategicPlans = sp;
    }

    protected StrategicObjective newEntity() {
        return new StrategicObjective();
    }

    protected StrategicObjective apply(StrategicObjectiveCreateRequest r, StrategicObjective e) {
        int start = r.startYear() != null ? r.startYear() : r.fiscalYear();
        int end = r.endYear() != null ? r.endYear() : start;
        if (end < start)
            throw new BadRequestException("endYear deve ser maior ou igual a startYear");
        e.setName(r.name());
        e.setDescription(r.description());
        e.setFiscalYear(r.fiscalYear());
        e.setStartYear(start);
        e.setEndYear(end);
        e.setPerspective(r.perspective());
        e.setStrategicElement(elements.findById(r.strategicElementId()));
        e.setStrategicPlan(r.strategicPlanId() == null ? null : strategicPlans.findById(r.strategicPlanId()));
        applyAnnualTargets(r, e, start, end);
        applyKpis(r, e, start, end);
        return e;
    }

    /**
     * Atualiza a meta de cada ano em vez de a apagar e recriar: o Hibernate insere antes de apagar no flush, e
     * uma meta nova para um ano já existente violaria {@code uk_annual_target_year}.
     */
    private void applyAnnualTargets(StrategicObjectiveCreateRequest r, StrategicObjective e, int start, int end) {
        List<StrategicObjectiveCreateRequest.AnnualTargetRequest> requested =
                r.annualTargets() == null ? List.of() : r.annualTargets();
        Set<Integer> years = new HashSet<>();
        for (StrategicObjectiveCreateRequest.AnnualTargetRequest t : requested) {
            if (t.year() < start || t.year() > end)
                throw new BadRequestException("annualTarget.year " + t.year() + " fora do intervalo do objetivo");
            if (!years.add(t.year()))
                throw new BadRequestException("annualTarget.year duplicado: " + t.year());
        }
        Map<Integer, StrategicObjectiveAnnualTarget> existing = new HashMap<>();
        e.getAnnualTargets().forEach(target -> existing.put(target.getYear(), target));
        e.getAnnualTargets().removeIf(target -> !years.contains(target.getYear()));
        for (StrategicObjectiveCreateRequest.AnnualTargetRequest t : requested) {
            StrategicObjectiveAnnualTarget target = existing.get(t.year());
            if (target == null) {
                target = new StrategicObjectiveAnnualTarget();
                target.setStrategicObjective(e);
                target.setYear(t.year());
                e.getAnnualTargets().add(target);
            }
            target.setTargetLabel(t.targetLabel());
            target.setTargetValue(t.targetValue());
            target.setWeight(t.weight());
        }
    }

    private void applyKpis(StrategicObjectiveCreateRequest r, StrategicObjective e, int start, int end) {
        e.getKpis().clear();
        if (r.kpis() == null)
            return;
        for (StrategicObjectiveCreateRequest.KpiRequest k : r.kpis()) {
            Kpi kpi = new Kpi();
            kpi.setName(k.name());
            kpi.setTarget(k.target());
            kpi.setCurrent(k.current());
            kpi.setGoal(k.goal());
            kpi.setStrategicObjective(e);
            if (k.measurements() != null) {
                Set<Integer> years = new HashSet<>();
                for (StrategicObjectiveCreateRequest.KpiMeasurementRequest m : k.measurements()) {
                    if (m.year() < start || m.year() > end)
                        throw new BadRequestException("measurement.year " + m.year() + " fora do intervalo do objetivo");
                    if (!years.add(m.year()))
                        throw new BadRequestException("measurement.year duplicado no mesmo KPI: " + m.year());
                    KpiMeasurement measurement = new KpiMeasurement();
                    measurement.setKpi(kpi);
                    measurement.setYear(m.year());
                    measurement.setCurrent(m.current());
                    measurement.setGoal(m.goal());
                    kpi.getMeasurements().add(measurement);
                }
            }
            e.getKpis().add(kpi);
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<StrategicObjective> findByElement(UUID id) {
        elements.findById(id);
        return objectives.findByStrategicElement_Id(id);
    }
}
