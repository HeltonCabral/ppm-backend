package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectTeamMemberRequest;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.ProjectTeamMemberRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/team-members")
public class ProjectTeamMemberController {
    private final ProjectRepository projects;
    private final ProjectTeamMemberRepository teamMembers;

    public ProjectTeamMemberController(ProjectRepository projects, ProjectTeamMemberRepository teamMembers) {
        this.projects = projects;
        this.teamMembers = teamMembers;
    }

    @GetMapping
    public List<ProjectTeamMemberResponse> list(@PathVariable UUID projectId) {
        return teamMembers.findByProjectId(projectId).stream()
                .map(ProjectTeamMemberResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectTeamMemberResponse create(@PathVariable UUID projectId,
            @Valid @RequestBody ProjectTeamMemberRequest request) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado: " + projectId));
        ProjectTeamMember member = new ProjectTeamMember();
        member.setProject(project);
        member.setCode(request.code() != null ? request.code().trim() : null);
        member.setDirectionName(request.directionName() != null ? request.directionName().trim() : null);
        member.setDirectionCode(request.directionCode() != null ? request.directionCode().trim() : null);
        member.setAreaName(request.areaName() != null ? request.areaName().trim() : null);
        member.setAreaCode(request.areaCode() != null ? request.areaCode().trim() : null);
        member.setName(request.name().trim());
        member.setEmail(request.email() != null ? request.email().trim() : null);
        member.setRole(request.role() != null ? request.role().trim() : null);
        member.setType(request.type());
        return ProjectTeamMemberResponse.from(teamMembers.save(member));
    }

    @PutMapping("/{id}")
    public ProjectTeamMemberResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody ProjectTeamMemberRequest request) {
        ProjectTeamMember member = teamMembers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado: " + id));
        if (!member.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Membro não pertence ao projeto");
        }
        member.setCode(request.code() != null ? request.code().trim() : null);
        member.setDirectionName(request.directionName() != null ? request.directionName().trim() : null);
        member.setDirectionCode(request.directionCode() != null ? request.directionCode().trim() : null);
        member.setAreaName(request.areaName() != null ? request.areaName().trim() : null);
        member.setAreaCode(request.areaCode() != null ? request.areaCode().trim() : null);
        member.setName(request.name().trim());
        member.setEmail(request.email() != null ? request.email().trim() : null);
        member.setRole(request.role() != null ? request.role().trim() : null);
        member.setType(request.type());
        return ProjectTeamMemberResponse.from(teamMembers.save(member));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        ProjectTeamMember member = teamMembers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado: " + id));
        if (!member.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Membro não pertence ao projeto");
        }
        teamMembers.delete(member);
    }
}
