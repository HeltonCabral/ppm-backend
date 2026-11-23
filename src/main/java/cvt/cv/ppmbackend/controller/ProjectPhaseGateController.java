package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectPhaseGateDtos.*;
import cvt.cv.ppmbackend.service.ProjectPhaseGateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/gates")
public class ProjectPhaseGateController {
    private final ProjectPhaseGateService gates;

    public ProjectPhaseGateController(ProjectPhaseGateService gates) { this.gates = gates; }

    @GetMapping
    public List<Response> list(@PathVariable UUID projectId) { return gates.list(projectId); }

    @PutMapping("/{phaseId}")
    public Response update(@PathVariable UUID projectId, @PathVariable UUID phaseId,
            @RequestBody UpdateRequest request, @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return gates.update(projectId, phaseId, request, actor);
    }

    @PostMapping("/{phaseId}/pass")
    public Response pass(@PathVariable UUID projectId, @PathVariable UUID phaseId,
            @RequestBody(required = false) PassRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return gates.pass(projectId, phaseId, request, actor);
    }

    @PostMapping("/{phaseId}/approve")
    public Response approve(@PathVariable UUID projectId, @PathVariable UUID phaseId,
            @RequestBody(required = false) DecisionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return gates.approve(projectId, phaseId, request, actor);
    }

    @PostMapping("/{phaseId}/reject")
    public Response reject(@PathVariable UUID projectId, @PathVariable UUID phaseId,
            @RequestBody DecisionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {
        return gates.reject(projectId, phaseId, request, actor);
    }
}
