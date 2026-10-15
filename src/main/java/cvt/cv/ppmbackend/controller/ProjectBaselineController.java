package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectBaselineDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.ProjectBaselineDtos.Response;
import cvt.cv.ppmbackend.service.ProjectBaselineService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/baselines")
public class ProjectBaselineController {
    private final ProjectBaselineService baselines;

    public ProjectBaselineController(ProjectBaselineService baselines) { this.baselines = baselines; }

    @GetMapping
    public List<Response> list(@PathVariable UUID projectId) { return baselines.list(projectId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable UUID projectId, @RequestBody CreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return baselines.create(projectId, request, actor);
    }
}
