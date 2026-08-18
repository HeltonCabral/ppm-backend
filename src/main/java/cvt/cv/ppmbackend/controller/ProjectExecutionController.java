package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectExecutionResponse;
import cvt.cv.ppmbackend.dto.ProjectExecutionUpdateRequest;
import cvt.cv.ppmbackend.service.ProjectExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/execution")
public class ProjectExecutionController {
    private final ProjectExecutionService executions;

    public ProjectExecutionController(ProjectExecutionService executions) {
        this.executions = executions;
    }

    @GetMapping
    public ProjectExecutionResponse get(@PathVariable UUID projectId) {
        return executions.get(projectId);
    }

    @PutMapping
    public ProjectExecutionResponse update(@PathVariable UUID projectId,
            @Valid @RequestBody ProjectExecutionUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return executions.update(projectId, request, actor);
    }
}
