package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectCreateRequest;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.service.ProjectService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
public class ProjectController extends AbstractCrudController<Project, ProjectCreateRequest> {
    private final ProjectService projects;

    public ProjectController(ProjectService s) {
        super(s);
        projects = s;
    }

    @Override
    @GetMapping
    public List<Project> findAll() {
        return projects.findAll();
    }

    @GetMapping(params = "operationalPlanId")
    public List<Project> byOperationalPlanParam(@RequestParam UUID operationalPlanId) {
        return projects.findByOperationalPlan(operationalPlanId);
    }

    @GetMapping("/status/{status}")
    public List<Project> byStatus(@PathVariable ProjectStatus status) {
        return projects.findByStatus(status);
    }

    @GetMapping("/domain/{domainId}")
    public List<Project> byDomain(@PathVariable UUID domainId) {
        return projects.findByDomain(domainId);
    }

    @GetMapping("/program/{id}")
    public List<Project> byProgram(@PathVariable UUID id) {
        return projects.findByProgram(id);
    }
}
