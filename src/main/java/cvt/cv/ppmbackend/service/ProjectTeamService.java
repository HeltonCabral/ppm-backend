package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectTeamMemberRequest;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectTeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Equipa do projeto: pessoas, o perfil que preenchem e a sua alocação (percentagem e período). Gravar nunca
 * recusa uma sobrealocação; a resposta traz {@code warnings} para o gestor decidir.
 */
@Service
@Transactional
public class ProjectTeamService {
    private final ProjectGuard guard;
    private final ProjectTeamMemberRepository teamMembers;
    private final ProjectProfileService projectProfiles;
    private final ProjectEventService events;
    private final ProjectCapacityService capacity;

    public ProjectTeamService(ProjectGuard guard, ProjectTeamMemberRepository teamMembers,
            ProjectProfileService projectProfiles, ProjectEventService events, ProjectCapacityService capacity) {
        this.guard = guard;
        this.teamMembers = teamMembers;
        this.projectProfiles = projectProfiles;
        this.events = events;
        this.capacity = capacity;
    }

    @Transactional(readOnly = true)
    public List<ProjectTeamMemberResponse> list(UUID projectId) {
        return teamMembers.findByProjectId(projectId).stream().map(ProjectTeamMemberResponse::from).toList();
    }

    public ProjectTeamMemberResponse create(UUID projectId, ProjectTeamMemberRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectTeamMember member = new ProjectTeamMember();
        member.setProject(project);
        apply(member, request, projectId);
        List<String> profileWarnings = profileWarnings(member, null);
        ProjectTeamMember saved = teamMembers.save(member);
        events.log(project, ProjectEventType.TEAM_CHANGED, describe(saved, "entrou na equipa"), actor);
        return ProjectTeamMemberResponse.from(saved, merge(profileWarnings, capacity.warningsFor(saved)));
    }

    public ProjectTeamMemberResponse update(UUID projectId, UUID memberId, ProjectTeamMemberRequest request,
            String actor) {
        guard.requireWritable(projectId);
        ProjectTeamMember member = require(projectId, memberId);
        UUID previousProfileId = member.getProjectProfile() == null ? null : member.getProjectProfile().getId();
        apply(member, request, projectId);
        List<String> profileWarnings = profileWarnings(member, previousProfileId);
        ProjectTeamMember saved = teamMembers.save(member);
        events.log(member.getProject(), ProjectEventType.TEAM_CHANGED, describe(saved, "alterado na equipa"), actor);
        return ProjectTeamMemberResponse.from(saved, merge(profileWarnings, capacity.warningsFor(saved)));
    }

    public void delete(UUID projectId, UUID memberId, String actor) {
        guard.requireWritable(projectId);
        ProjectTeamMember member = require(projectId, memberId);
        teamMembers.delete(member);
        events.log(member.getProject(), ProjectEventType.TEAM_CHANGED, describe(member, "saiu da equipa"), actor);
    }

    private ProjectTeamMember require(UUID projectId, UUID memberId) {
        ProjectTeamMember member = teamMembers.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado: " + memberId));
        if (!member.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Membro não pertence ao projeto");
        }
        return member;
    }

    private void apply(ProjectTeamMember member, ProjectTeamMemberRequest request, UUID projectId) {
        int allocation = request.allocationPercentage() == null ? 100 : request.allocationPercentage();
        if (allocation < 1 || allocation > 100) {
            throw new BadRequestException("A alocação tem de estar entre 1 e 100%.");
        }
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("O fim da alocação não pode ser anterior ao início.");
        }
        member.setCode(trim(request.code()));
        member.setDirectionName(trim(request.directionName()));
        member.setDirectionCode(trim(request.directionCode()));
        member.setAreaName(trim(request.areaName()));
        member.setAreaCode(trim(request.areaCode()));
        member.setName(request.name().trim());
        member.setEmail(trim(request.email()));
        member.setRole(trim(request.role()));
        member.setType(request.type());
        member.setAllocationPercentage(allocation);
        member.setStartDate(request.startDate());
        member.setEndDate(request.endDate());
        ProjectProfileService.applyProfileDefaults(member,
                projectProfiles.resolveForMember(projectId, request.projectProfileId()));
    }

    /** Associar a um perfil que já tem as pessoas previstas: aceita-se, com aviso. */
    private List<String> profileWarnings(ProjectTeamMember member, UUID previousProfileId) {
        var profile = member.getProjectProfile();
        if (profile == null || profile.getId().equals(previousProfileId)) {
            return List.of();
        }
        long others = teamMembers.findByProjectProfileId(profile.getId()).stream()
                .filter(m -> !m.getId().equals(member.getId()))
                .count();
        int required = profile.getRequiredQuantity() == null ? 0 : profile.getRequiredQuantity();
        if (others < required) {
            return List.of();
        }
        return List.of(profile.getProfileName() + " já tem " + (required == 1 ? "a 1 pessoa prevista"
                : "as " + required + " pessoas previstas") + "; " + member.getName() + " fica a mais.");
    }

    private static List<String> merge(List<String> first, List<String> second) {
        List<String> all = new java.util.ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static String describe(ProjectTeamMember member, String what) {
        int allocation = member.getAllocationPercentage() == null ? 100 : member.getAllocationPercentage();
        return member.getName() + " " + what
                + (member.getProjectProfile() == null ? "" : " (" + member.getProjectProfile().getProfileName() + ")")
                + (allocation == 100 ? "" : " a " + allocation + "%");
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
