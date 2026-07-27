package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.OperationalPlanCreateRequest;
import cvt.cv.ppmbackend.dto.OperationalPlanSummary;
import cvt.cv.ppmbackend.entity.OperationalPlan;
import cvt.cv.ppmbackend.entity.OperationalPlanBaseline;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.OperationalPlanStatus;
import cvt.cv.ppmbackend.service.OperationalPlanService;
import cvt.cv.ppmbackend.service.ProjectService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/operational-plans")
public class OperationalPlanController
        extends AbstractCrudController<OperationalPlan, OperationalPlanCreateRequest> {
    private final OperationalPlanService plans;
    private final ProjectService projects;

    public OperationalPlanController(OperationalPlanService s, ProjectService p) {
        super(s);
        plans = s;
        projects = p;
    }

    @Override
    @GetMapping
    public List<OperationalPlan> findAll() {
        return plans.search(null, null, null);
    }

    @GetMapping(params = { "strategicPlanId" })
    public List<OperationalPlan> byStrategicPlan(@RequestParam UUID strategicPlanId,
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) String status) {
        return plans.search(strategicPlanId, fiscalYear, status == null ? null : OperationalPlanStatus.of(status));
    }

    @GetMapping(params = { "fiscalYear" })
    public List<OperationalPlan> byFiscalYear(@RequestParam Integer fiscalYear) {
        return plans.search(null, fiscalYear, null);
    }

    @GetMapping(params = { "status" })
    public List<OperationalPlan> byStatus(@RequestParam String status) {
        return plans.search(null, null, OperationalPlanStatus.of(status));
    }

    @PostMapping("/{id}/approve")
    public OperationalPlan approve(@PathVariable UUID id) {
        return plans.approve(id);
    }

    @PostMapping("/{id}/close")
    public OperationalPlan close(@PathVariable UUID id) {
        return plans.close(id);
    }

    @GetMapping("/{id}/projects")
    public List<Project> projectsOf(@PathVariable UUID id) {
        return projects.findByOperationalPlan(id);
    }

    @GetMapping("/{id}/baseline")
    public List<OperationalPlanBaseline> baselinesOf(@PathVariable UUID id) {
        return plans.baselinesOf(id);
    }

    @GetMapping("/{id}/summary")
    public OperationalPlanSummary summary(@PathVariable UUID id) {
        return plans.summary(id);
    }
}
