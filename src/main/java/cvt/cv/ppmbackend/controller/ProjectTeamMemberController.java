package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectTeamMemberRequest;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.ProjectTeamMemberRepository;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.service.ProjectEventService;
import cvt.cv.ppmbackend.service.ProjectProfileService;
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
    private final ProjectProfileService projectProfiles;
    private final ProjectEventService events;

    public ProjectTeamMemberController(ProjectRepository projects, ProjectTeamMemberRepository teamMembers,
            ProjectProfileService projectProfiles, ProjectEventService events) {
        this.projects = projects;
        this.teamMembers = teamMembers;
        this.projectProfiles = projectProfiles;
        this.events = events;
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
            @Valid @RequestBody ProjectTeamMemberRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado: " + projectId));
        ProjectTeamMember member = new ProjectTeamMember();
        member.setProject(project);
        apply(member, request, projectId);
        ProjectTeamMember saved = teamMembers.save(member);
        events.log(project, ProjectEventType.TEAM_CHANGED, describe(saved, "entrou na equipa"), user);
        return ProjectTeamMemberResponse.from(saved);
    }

    @PutMapping("/{id}")
    public ProjectTeamMemberResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody ProjectTeamMemberRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        ProjectTeamMember member = teamMembers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado: " + id));
        if (!member.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Membro não pertence ao projeto");
        }
        apply(member, request, projectId);
        ProjectTeamMember saved = teamMembers.save(member);
        events.log(member.getProject(), ProjectEventType.TEAM_CHANGED, describe(saved, "alterado na equipa"), user);
        return ProjectTeamMemberResponse.from(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        ProjectTeamMember member = teamMembers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado: " + id));
        if (!member.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Membro não pertence ao projeto");
        }
        teamMembers.delete(member);
        events.log(member.getProject(), ProjectEventType.TEAM_CHANGED, describe(member, "saiu da equipa"), user);
    }

    private static String describe(ProjectTeamMember member, String what) {
        return member.getName() + " " + what
                + (member.getProjectProfile() == null ? "" : " (" + member.getProjectProfile().getProfileName() + ")");
    }

    private void apply(ProjectTeamMember member, ProjectTeamMemberRequest request, UUID projectId) {
        member.setCode(request.code() != null ? request.code().trim() : null);
        member.setDirectionName(request.directionName() != null ? request.directionName().trim() : null);
        member.setDirectionCode(request.directionCode() != null ? request.directionCode().trim() : null);
        member.setAreaName(request.areaName() != null ? request.areaName().trim() : null);
        member.setAreaCode(request.areaCode() != null ? request.areaCode().trim() : null);
        member.setName(request.name().trim());
        member.setEmail(request.email() != null ? request.email().trim() : null);
        member.setRole(request.role() != null ? request.role().trim() : null);
        member.setType(request.type());
        ProjectProfileService.applyProfileDefaults(member,
                projectProfiles.resolveForMember(projectId, request.projectProfileId()));
    }
}
