package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectProfileRequest;
import cvt.cv.ppmbackend.dto.ProjectProfileResponse;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberResponse;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.ProjectProfileOrigin;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import cvt.cv.ppmbackend.repository.ProjectProfileRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.ProjectTeamMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Perfis previstos de um projeto e as pessoas da equipa associadas a cada um. Os perfis vêm da Demanda
 * na aprovação ({@code origin = DEMAND}); na execução o gestor pode ajustá-los, remover os que já não
 * servem e acrescentar outros ({@code origin = PROJECT}).
 */
@Service
@Transactional(readOnly = true)
public class ProjectProfileService {
    private static final String PHASE_SEPARATOR = "; ";
    private static final int MAX_PHASE_NAMES = 1000;
    private static final Set<ProjectStatus> CLOSED_STATUSES = Set.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED);

    private final ProjectRepository projects;
    private final ProjectProfileRepository projectProfiles;
    private final ProjectTeamMemberRepository teamMembers;
    private final ProfileRepository profiles;
    private final ProjectEventService events;

    public ProjectProfileService(ProjectRepository projects, ProjectProfileRepository projectProfiles,
            ProjectTeamMemberRepository teamMembers, ProfileRepository profiles, ProjectEventService events) {
        this.projects = projects;
        this.projectProfiles = projectProfiles;
        this.teamMembers = teamMembers;
        this.profiles = profiles;
        this.events = events;
    }

    public List<ProjectProfileResponse> list(UUID projectId) {
        if (!projects.existsById(projectId)) {
            throw new ResourceNotFoundException("Projeto não encontrado: " + projectId);
        }
        Map<UUID, List<ProjectTeamMemberResponse>> membersByProfile = teamMembers.findByProjectId(projectId).stream()
                .filter(member -> member.getProjectProfile() != null)
                .collect(Collectors.groupingBy(member -> member.getProjectProfile().getId(),
                        Collectors.mapping(ProjectTeamMemberResponse::from, Collectors.toList())));
        return projectProfiles.findByProjectIdOrderByProfileNameAsc(projectId).stream()
                .map(profile -> toResponse(profile, membersByProfile.getOrDefault(profile.getId(), List.of())))
                .toList();
    }

    /** Acrescenta um perfil que a Demanda não previa. */
    @Transactional
    public ProjectProfileResponse create(UUID projectId, ProjectProfileRequest request, String actorId) {
        Project project = requireEditableProject(projectId);
        if (request == null || request.profileId() == null) {
            throw new BadRequestException("profileId é obrigatório.");
        }
        int quantity = requireQuantity(request.requiredQuantity());
        Profile profile = profiles.findById(request.profileId())
                .orElseThrow(() -> new BadRequestException("Perfil não encontrado: " + request.profileId()));
        if (!profile.isActive()) {
            throw new BadRequestException("O perfil " + profile.getName() + " está inativo.");
        }
        if (projectProfiles.existsByProjectIdAndProfile_Id(projectId, profile.getId())) {
            throw new DomainException(HttpStatus.CONFLICT, "PROJECT_PROFILE_ALREADY_EXISTS",
                    "O projeto já tem o perfil " + profile.getName() + ". Altere-o em vez de o repetir.",
                    Map.of("profileId", profile.getId().toString()));
        }
        ProjectProfile projectProfile = new ProjectProfile();
        projectProfile.setProject(project);
        projectProfile.setProfile(profile);
        projectProfile.setProfileName(truncate(profile.getName(), 150));
        projectProfile.setDirectionCode(truncate(trimToNull(profile.getDirectionCode()), 60));
        projectProfile.setDirectionName(truncate(trimToNull(profile.getDirectionName()), 120));
        projectProfile.setRequiredQuantity(quantity);
        projectProfile.setPhaseNames(phaseNames(project, request.phases(), List.of()));
        projectProfile.setOrigin(ProjectProfileOrigin.PROJECT);
        projectProfile.setCreatedBy(actor(actorId));
        projectProfile.setUpdatedBy(actor(actorId));
        project.getProfiles().add(projectProfile);
        ProjectProfile saved = projectProfiles.save(projectProfile);
        events.log(project, ProjectEventType.PROFILE_CHANGED, "Perfil " + saved.getProfileName() + " acrescentado ("
                + people(quantity) + ")", actorId);
        return toResponse(saved, List.of());
    }

    /** Ajusta n.º de pessoas e fases. O perfil de capacidade e a origem mantêm-se. */
    @Transactional
    public ProjectProfileResponse update(UUID projectId, UUID projectProfileId, ProjectProfileRequest request,
            String actorId) {
        Project project = requireEditableProject(projectId);
        ProjectProfile projectProfile = requireOwned(projectId, projectProfileId);
        if (request == null) {
            throw new BadRequestException("Dados do perfil em falta.");
        }
        if (request.profileId() != null && !request.profileId().equals(projectProfile.getProfile().getId())) {
            throw new BadRequestException(
                    "O perfil de capacidade não se muda. Remova este perfil e acrescente o novo.");
        }
        int previousQuantity = projectProfile.getRequiredQuantity() == null ? 0 : projectProfile.getRequiredQuantity();
        projectProfile.setRequiredQuantity(requireQuantity(request.requiredQuantity()));
        events.log(project, ProjectEventType.PROFILE_CHANGED, "Perfil " + projectProfile.getProfileName()
                + " alterado: " + previousQuantity + " → " + people(projectProfile.getRequiredQuantity()), actorId);
        projectProfile.setPhaseNames(phaseNames(project, request.phases(), splitPhases(projectProfile.getPhaseNames())));
        projectProfile.setUpdatedBy(actor(actorId));
        ProjectProfile saved = projectProfiles.save(projectProfile);
        List<ProjectTeamMemberResponse> members = teamMembers.findByProjectProfileId(saved.getId()).stream()
                .map(ProjectTeamMemberResponse::from)
                .toList();
        return toResponse(saved, members);
    }

    /** Remove o perfil; as pessoas associadas continuam na equipa, sem perfil. */
    @Transactional
    public void delete(UUID projectId, UUID projectProfileId, String actorId) {
        Project project = requireEditableProject(projectId);
        ProjectProfile projectProfile = requireOwned(projectId, projectProfileId);
        List<ProjectTeamMember> members = teamMembers.findByProjectProfileId(projectProfile.getId());
        members.forEach(member -> member.setProjectProfile(null));
        teamMembers.saveAll(members);
        teamMembers.flush();
        project.getProfiles().remove(projectProfile);
        projectProfiles.delete(projectProfile);
        events.log(project, ProjectEventType.PROFILE_CHANGED, "Perfil " + projectProfile.getProfileName() + " removido"
                + (members.isEmpty() ? "" : " (" + members.size() + " pessoa(s) ficaram sem perfil)"), actorId);
    }

    private static String people(int quantity) {
        return quantity + (quantity == 1 ? " pessoa" : " pessoas");
    }

    /** Perfil indicado num membro da equipa: tem de pertencer ao mesmo projeto. */
    public ProjectProfile resolveForMember(UUID projectId, UUID projectProfileId) {
        if (projectProfileId == null) {
            return null;
        }
        ProjectProfile profile = projectProfiles.findById(projectProfileId)
                .orElseThrow(() -> new BadRequestException("Perfil do projeto não encontrado: " + projectProfileId));
        if (!profile.getProject().getId().equals(projectId)) {
            throw new BadRequestException("O perfil indicado não pertence a este projeto.");
        }
        return profile;
    }

    /** Com perfil, o papel e a direção do membro vêm do perfil quando não foram indicados. */
    public static void applyProfileDefaults(ProjectTeamMember member, ProjectProfile profile) {
        member.setProjectProfile(profile);
        if (profile == null) {
            return;
        }
        if (member.getRole() == null || member.getRole().isBlank()) {
            member.setRole(truncate(profile.getProfileName(), 100));
        }
        if ((member.getDirectionCode() == null || member.getDirectionCode().isBlank())
                && profile.getDirectionCode() != null) {
            member.setDirectionCode(profile.getDirectionCode());
            member.setDirectionName(profile.getDirectionName());
        }
    }

    /** Fases guardadas como texto, pela ordem gravada. */
    public static List<String> splitPhases(String phaseNames) {
        return phaseNames == null ? List.of()
                : Arrays.stream(phaseNames.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private Project requireEditableProject(UUID projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado: " + projectId));
        if (CLOSED_STATUSES.contains(project.getStatus())) {
            throw new DomainException(HttpStatus.CONFLICT, "PROJECT_CLOSED",
                    "Um projeto concluído ou cancelado não pode alterar os perfis.",
                    Map.of("status", project.getStatus().name()));
        }
        return project;
    }

    private ProjectProfile requireOwned(UUID projectId, UUID projectProfileId) {
        return projectProfiles.findById(projectProfileId)
                .filter(profile -> profile.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil do projeto não encontrado: " + projectProfileId));
    }

    private static int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BadRequestException("requiredQuantity tem de ser um número inteiro igual ou superior a 1.");
        }
        return quantity;
    }

    /**
     * Valida e junta as fases pela ordem recebida, sem repetidos. Com Tipo de origem, cada fase tem de
     * existir nele (com a grafia do Tipo); as fases que o perfil já tinha continuam aceites, para não
     * impedir a alteração de um perfil herdado de um planeamento personalizado.
     */
    private String phaseNames(Project project, List<String> requested, List<String> current) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        Map<String, String> known = new LinkedHashMap<>();
        current.forEach(name -> known.put(name.toLowerCase(Locale.ROOT), name));
        List<PhaseDefinition> typePhases = project.getPhaseModel() == null ? List.of()
                : project.getPhaseModel().getPhases().stream().filter(PhaseDefinition::isActive).toList();
        typePhases.forEach(phase -> known.put(phase.getName().trim().toLowerCase(Locale.ROOT), phase.getName().trim()));
        boolean validateAgainstType = !typePhases.isEmpty();

        List<String> names = new ArrayList<>();
        for (String raw : requested) {
            String name = trimToNull(raw);
            if (name == null) {
                continue;
            }
            if (name.contains(";")) {
                throw new BadRequestException("O nome da fase não pode conter \";\": " + name);
            }
            String canonical = known.get(name.toLowerCase(Locale.ROOT));
            if (canonical == null) {
                if (validateAgainstType) {
                    throw new BadRequestException("A fase \"" + name + "\" não existe no Tipo "
                            + project.getPhaseModel().getName() + ".");
                }
                canonical = name;
            }
            if (!names.contains(canonical)) {
                names.add(canonical);
            }
        }
        String joined = String.join(PHASE_SEPARATOR, names);
        if (joined.length() > MAX_PHASE_NAMES) {
            throw new BadRequestException("A lista de fases é demasiado longa.");
        }
        return joined.isEmpty() ? null : joined;
    }

    static ProjectProfileResponse toResponse(ProjectProfile profile, List<ProjectTeamMemberResponse> members) {
        int required = profile.getRequiredQuantity() == null ? 0 : profile.getRequiredQuantity();
        return new ProjectProfileResponse(profile.getId(), profile.getProfile().getId(), profile.getProfileName(),
                profile.getDirectionCode(), profile.getDirectionName(), profile.getRequiredQuantity(), members.size(),
                Math.max(0, required - members.size()), splitPhases(profile.getPhaseNames()), members,
                originOf(profile));
    }

    public static ProjectProfileOrigin originOf(ProjectProfile profile) {
        return profile.getOrigin() == null ? ProjectProfileOrigin.DEMAND : profile.getOrigin();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
