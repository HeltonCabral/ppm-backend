package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectTeamMemberRequest;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberResponse;
import cvt.cv.ppmbackend.service.ProjectTeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/team-members")
public class ProjectTeamMemberController {
    private final ProjectTeamService team;

    public ProjectTeamMemberController(ProjectTeamService team) {
        this.team = team;
    }

    @GetMapping
    public List<ProjectTeamMemberResponse> list(@PathVariable UUID projectId) {
        return team.list(projectId);
    }

    /** Nunca recusa uma sobrealocação: a resposta traz {@code warnings}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectTeamMemberResponse create(@PathVariable UUID projectId,
            @Valid @RequestBody ProjectTeamMemberRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return team.create(projectId, request, user);
    }

    @PutMapping("/{id}")
    public ProjectTeamMemberResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody ProjectTeamMemberRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return team.update(projectId, id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        team.delete(projectId, id, user);
    }
}
