package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectDependencyDtos.Request;
import cvt.cv.ppmbackend.dto.ProjectDependencyDtos.Response;
import cvt.cv.ppmbackend.service.ProjectDependencyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/project-dependencies")
public class ProjectDependencyController {
    private final ProjectDependencyService dependencies;

    public ProjectDependencyController(ProjectDependencyService dependencies) { this.dependencies = dependencies; }

    @GetMapping
    public List<Response> list(@RequestParam(required = false) UUID projectId) {
        return dependencies.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody Request request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return dependencies.create(request, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        dependencies.delete(id, actor);
    }
}
