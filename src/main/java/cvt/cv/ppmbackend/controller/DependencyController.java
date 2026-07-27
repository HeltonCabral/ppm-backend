package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.DependencyCreateRequest;
import cvt.cv.ppmbackend.entity.Dependency;
import cvt.cv.ppmbackend.service.DependencyService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/dependencies")
public class DependencyController extends AbstractCrudController<Dependency, DependencyCreateRequest> {
    private final DependencyService dependencies;

    public DependencyController(DependencyService s) {
        super(s);
        dependencies = s;
    }

    @GetMapping("/project/{id}")
    public List<Dependency> byProject(@PathVariable UUID id) {
        return dependencies.findByProject(id);
    }
}
