package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicElementCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicElement;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class StrategicElementService
        extends AbstractCrudService<StrategicElement, StrategicElementCreateRequest> {
    private final StrategicElementRepository elements;
    private final StrategicPillarService pillars;

    private final cvt.cv.ppmbackend.repository.StrategicObjectiveRepository objectives;
    private final cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository planningCycles;

    public StrategicElementService(StrategicElementRepository r, StrategicPillarService p,
            cvt.cv.ppmbackend.repository.StrategicObjectiveRepository objectives,
            cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository planningCycles) {
        super(r, "Elemento estratégico");
        elements = r;
        pillars = p;
        this.objectives = objectives;
        this.planningCycles = planningCycles;
    }

    /**
     * Um elemento ainda usado não se apaga: o Hibernate falharia no flush com uma TransientObjectException (500).
     * Responde 409 com o que o impede.
     */
    @Override
    public void delete(UUID id) {
        StrategicElement element = findById(id);
        java.util.Map<String, Long> usages = new java.util.LinkedHashMap<>();
        usages.put("objetivos estratégicos", objectives.countByStrategicElement_Id(id));
        usages.put("demandas", planningCycles.countByStrategicElement_Id(id));
        StrategicUsage.requireUnused("o elemento", element.getName(), "STRATEGIC_ELEMENT_IN_USE", usages);
        super.delete(id);
    }

    protected StrategicElement newEntity() {
        return new StrategicElement();
    }

    protected StrategicElement apply(StrategicElementCreateRequest r, StrategicElement e) {
        e.setName(r.name());
        e.setDescription(r.description());
        e.setIcon(r.icon());
        e.setDisplayOrder(r.displayOrder());
        e.setStrategicPillar(pillars.findById(r.strategicPillarId()));
        return e;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<StrategicElement> findByPillar(UUID id) {
        pillars.findById(id);
        return elements.findByStrategicPillar_Id(id);
    }
}
