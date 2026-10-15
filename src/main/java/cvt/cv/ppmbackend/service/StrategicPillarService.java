package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicPillarCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicPillar;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StrategicPillarService extends AbstractCrudService<StrategicPillar, StrategicPillarCreateRequest> {
    private final StrategicPlanService strategicPlans;
    private final StrategicElementRepository elements;
    private final DemandPlanningCycleRepository planningCycles;
    private final ProjectRepository projects;
    private final ProgramRepository programs;

    public StrategicPillarService(StrategicPillarRepository r, StrategicPlanService sp,
            StrategicElementRepository elements, DemandPlanningCycleRepository planningCycles,
            ProjectRepository projects, ProgramRepository programs) {
        super(r, "Pilar estratégico");
        strategicPlans = sp;
        this.elements = elements;
        this.planningCycles = planningCycles;
        this.projects = projects;
        this.programs = programs;
    }

    protected StrategicPillar newEntity() {
        return new StrategicPillar();
    }

    protected StrategicPillar apply(StrategicPillarCreateRequest r, StrategicPillar e) {
        e.setName(r.name());
        e.setDescription(r.description());
        e.setIcon(r.icon());
        e.setDisplayOrder(r.displayOrder());
        e.setStrategicPlan(r.strategicPlanId() == null ? null : strategicPlans.findById(r.strategicPlanId()));
        return e;
    }

    /**
     * Um pilar ainda usado não se apaga: o Hibernate falharia no flush com uma TransientObjectException (500).
     * Responde 409 com o que o impede.
     */
    @Override
    public void delete(UUID id) {
        StrategicPillar pillar = findById(id);
        Map<String, Long> usages = new LinkedHashMap<>();
        usages.put("elementos estratégicos", elements.countByStrategicPillar_Id(id));
        usages.put("demandas", planningCycles.countByStrategicPillar_Id(id));
        usages.put("projetos", projects.countByStrategicPillar_Id(id));
        usages.put("programas", programs.countByStrategicPillar_Id(id));
        StrategicUsage.requireUnused("o pilar", pillar.getName(), "STRATEGIC_PILLAR_IN_USE", usages);
        super.delete(id);
    }
}
