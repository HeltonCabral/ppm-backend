package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectCostDtos.Financials;
import cvt.cv.ppmbackend.dto.ProjectCostDtos.Request;
import cvt.cv.ppmbackend.dto.ProjectCostDtos.Response;
import cvt.cv.ppmbackend.service.ProjectCostService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}")
public class ProjectCostController {
    private final ProjectCostService costs;

    public ProjectCostController(ProjectCostService costs) { this.costs = costs; }

    @GetMapping("/costs")
    public List<Response> list(@PathVariable UUID projectId) { return costs.list(projectId); }

    @PostMapping("/costs")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable UUID projectId, @RequestBody Request request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return costs.create(projectId, request, actor);
    }

    @PutMapping("/costs/{costId}")
    public Response update(@PathVariable UUID projectId, @PathVariable UUID costId, @RequestBody Request request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return costs.update(projectId, costId, request, actor);
    }

    @DeleteMapping("/costs/{costId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID costId,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        costs.delete(projectId, costId, actor);
    }

    @GetMapping("/financials")
    public Financials financials(@PathVariable UUID projectId) { return costs.financials(projectId); }
}
