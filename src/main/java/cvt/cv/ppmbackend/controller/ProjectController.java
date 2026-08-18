package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.*;
import cvt.cv.ppmbackend.dto.ProjectExecutionRankDtos.ReprioritizeRequest;
import cvt.cv.ppmbackend.dto.ProjectExecutionRankDtos.ReprioritizeResponse;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/projects")
public class ProjectController {
    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public PageResponse<ProjectListItemResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ProjectOrigin origin,
            @RequestParam(required = false) String directionCode,
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) RiskLevel risk) {
        return projects.list(page, size, origin, directionCode, areaCode, domain, status, risk);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return projects.get(id);
    }

    @PostMapping("/extra-plan")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createExtraPlan(@Valid @RequestBody ProjectCreateExtraPlanRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return projects.createExtraPlan(request, actor);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody ProjectUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return projects.update(id, request, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        projects.delete(id);
    }

    @PatchMapping("/{id}/status")
    public ProjectResponse updateStatus(@PathVariable UUID id,
            @Valid @RequestBody ProjectStatusUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return projects.updateStatus(id, request.status(), actor);
    }

    @PostMapping("/{id}/start")
    public ProjectResponse start(@PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return projects.start(id, actor);
    }

    @PostMapping("/{id}/complete")
    public ProjectResponse complete(@PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return projects.complete(id, actor);
    }

    @PostMapping("/{projectId}/reprioritize-execution-rank")
    public ReprioritizeResponse reprioritize(@PathVariable UUID projectId,
            @Valid @RequestBody ReprioritizeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return projects.reprioritize(projectId, request, actor);
    }
}
