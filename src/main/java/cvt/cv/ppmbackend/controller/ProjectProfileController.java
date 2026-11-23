package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectProfileRequest;
import cvt.cv.ppmbackend.dto.ProjectProfileResponse;
import cvt.cv.ppmbackend.service.ProjectProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Perfis previstos do projeto: herdados da Demanda e ajustáveis na execução. */
@RestController
@RequestMapping("/projects/{projectId}/profiles")
public class ProjectProfileController {
    private final ProjectProfileService projectProfiles;

    public ProjectProfileController(ProjectProfileService projectProfiles) {
        this.projectProfiles = projectProfiles;
    }

    /** Perfis previstos com as pessoas já associadas e quantas faltam. */
    @GetMapping
    public List<ProjectProfileResponse> list(@PathVariable UUID projectId) {
        return projectProfiles.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectProfileResponse create(@PathVariable UUID projectId, @RequestBody ProjectProfileRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return projectProfiles.create(projectId, request, user);
    }

    @PutMapping("/{projectProfileId}")
    public ProjectProfileResponse update(@PathVariable UUID projectId, @PathVariable UUID projectProfileId,
            @RequestBody ProjectProfileRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return projectProfiles.update(projectId, projectProfileId, request, user);
    }

    /** As pessoas do perfil continuam na equipa, sem perfil. */
    @DeleteMapping("/{projectProfileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID projectProfileId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        projectProfiles.delete(projectId, projectProfileId, user);
    }
}
