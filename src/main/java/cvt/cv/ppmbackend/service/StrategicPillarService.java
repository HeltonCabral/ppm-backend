package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicPillarCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicPillar;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import org.springframework.stereotype.Service;

@Service
public class StrategicPillarService extends AbstractCrudService<StrategicPillar, StrategicPillarCreateRequest> {
    private final StrategicPlanService strategicPlans;

    public StrategicPillarService(StrategicPillarRepository r, StrategicPlanService sp) {
        super(r, "Pilar estratégico");
        strategicPlans = sp;
    }

    protected StrategicPillar newEntity() {
        return new StrategicPillar();
    }

    protected StrategicPillar apply(StrategicPillarCreateRequest r, StrategicPillar e) {
        e.setName(r.name());
        e.setDescription(r.description());
        e.setIcon(r.icon());
        e.setStrategicPlan(r.strategicPlanId() == null ? null : strategicPlans.findById(r.strategicPlanId()));
        return e;
    }
}
