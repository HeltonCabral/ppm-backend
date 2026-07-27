package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicPlanCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StrategicPlanService extends AbstractCrudService<StrategicPlan, StrategicPlanCreateRequest> {
    private final StrategicPlanRepository plans;

    public StrategicPlanService(StrategicPlanRepository r) {
        super(r, "Plano estratégico");
        plans = r;
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
}
