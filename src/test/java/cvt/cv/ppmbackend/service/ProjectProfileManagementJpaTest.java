package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectProfileRequest;
import cvt.cv.ppmbackend.dto.ProjectProfileResponse;
import cvt.cv.ppmbackend.dto.ProjectResponse;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.MemberType;
import cvt.cv.ppmbackend.enums.ProjectProfileOrigin;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import cvt.cv.ppmbackend.repository.ProjectProfileRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.ProjectTeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gestão dos perfis previstos do projeto, com base de dados real. */
@DataJpaTest
class ProjectProfileManagementJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectProfileRepository projectProfiles;
    @Autowired private ProjectTeamMemberRepository teamMembers;
    @Autowired private ProfileRepository profiles;
    @Autowired private cvt.cv.ppmbackend.repository.ProjectEventRepository eventRepository;

    private ProjectProfileService service;
    private Project project;
    private Profile analyst;
    private Profile developer;
    private Profile retired;
    private ProjectProfile inheritedAnalyst;

    @BeforeEach
    void setUp() {
        service = new ProjectProfileService(projects, projectProfiles, teamMembers, profiles,
                new ProjectEventService(eventRepository));

        PhaseModel type = new PhaseModel();
        type.setCode("PROJ_TI");
        type.setName("Projeto TI");
        em.persist(type);
        phase(type, "ANA", "Análise", 1);
        phase(type, "DEV", "Desenvolvimento", 2);
        phase(type, "TST", "Testes", 3);

        analyst = profile("Analista", true);
        developer = profile("Programador", true);
        retired = profile("Perfil Antigo", false);

        project = new Project();
        project.setName("Portal de clientes");
        project.setStatus(ProjectStatus.PLANNED);
        project.setPhaseModel(type);
        em.persist(project);

        inheritedAnalyst = new ProjectProfile();
        inheritedAnalyst.setProject(project);
        inheritedAnalyst.setProfile(analyst);
        inheritedAnalyst.setProfileName("Analista");
        inheritedAnalyst.setRequiredQuantity(1);
        inheritedAnalyst.setPhaseNames("Análise");
        em.persist(inheritedAnalyst);
        project.getProfiles().add(inheritedAnalyst);
        em.flush();
    }

    // 1. POST com um perfil novo → origin PROJECT, aparece na lista e no detalhe do projeto.
    @Test
    void addingAProfileMarksItAsAddedInTheProject() {
        ProjectProfileResponse created = service.create(project.getId(),
                new ProjectProfileRequest(developer.getId(), 2, List.of("Desenvolvimento")), "cvt00983");
        reload();

        assertThat(created.origin()).isEqualTo(ProjectProfileOrigin.PROJECT);
        assertThat(created.requiredQuantity()).isEqualTo(2);
        assertThat(created.missing()).isEqualTo(2);
        assertThat(service.list(project.getId())).extracting(ProjectProfileResponse::profileName,
                        ProjectProfileResponse::origin)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Analista", ProjectProfileOrigin.DEMAND),
                        org.assertj.core.api.Assertions.tuple("Programador", ProjectProfileOrigin.PROJECT));
        assertThat(ProjectResponse.detailed(em.find(Project.class, project.getId())).profiles())
                .extracting(ProjectResponse.ProfileSummary::profileName, ProjectResponse.ProfileSummary::origin)
                .contains(org.assertj.core.api.Assertions.tuple("Programador", ProjectProfileOrigin.PROJECT));
        assertThat(em.find(ProjectProfile.class, created.id()).getCreatedBy()).isEqualTo("cvt00983");
    }

    // 2. POST com um perfil repetido → 409.
    @Test
    void repeatedProfileIsAConflict() {
        assertThatThrownBy(() -> service.create(project.getId(),
                new ProjectProfileRequest(analyst.getId(), 1, List.of()), "cvt00983"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getMessage())
                            .isEqualTo("O projeto já tem o perfil Analista. Altere-o em vez de o repetir.");
                });
    }

    @Test
    void unknownOrInactiveProfileIsRejected() {
        assertThatThrownBy(() -> service.create(project.getId(),
                new ProjectProfileRequest(UUID.randomUUID(), 1, List.of()), "x"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(project.getId(),
                new ProjectProfileRequest(retired.getId(), 1, List.of()), "x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inativo");
    }

    // 3. PUT 1 → 3 recalcula missing; PUT com 0 → 400. 7. Um perfil DEMAND alterado continua DEMAND.
    @Test
    void changingTheQuantityRecalculatesMissingAndKeepsTheOrigin() {
        member("Maria", inheritedAnalyst);

        ProjectProfileResponse updated = service.update(project.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(null, 3, List.of("Análise")), "cvt00983");

        assertThat(updated.requiredQuantity()).isEqualTo(3);
        assertThat(updated.assignedCount()).isEqualTo(1);
        assertThat(updated.missing()).isEqualTo(2);
        assertThat(updated.origin()).isEqualTo(ProjectProfileOrigin.DEMAND);
        assertThatThrownBy(() -> service.update(project.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(null, 0, List.of()), "cvt00983"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void quantityBelowAssignedPeopleIsAllowed() {
        member("Maria", inheritedAnalyst);
        member("João", inheritedAnalyst);

        ProjectProfileResponse updated = service.update(project.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(null, 1, List.of()), "x");

        assertThat(updated.assignedCount()).isEqualTo(2);
        assertThat(updated.missing()).isZero();
    }

    // 4. PUT com phases → devolvidas na mesma ordem; fases fora do Tipo recusadas.
    @Test
    void phasesKeepTheGivenOrderAndAreValidatedAgainstTheType() {
        ProjectProfileResponse updated = service.update(project.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(null, 1, List.of("testes", "Análise", "Testes")), "x");

        assertThat(updated.phases()).containsExactly("Testes", "Análise");
        assertThatThrownBy(() -> service.update(project.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(null, 1, List.of("Produção")), "x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A fase \"Produção\" não existe no Tipo Projeto TI.");
    }

    @Test
    void capacityProfileCannotBeSwappedOnAnExistingRecord() {
        assertThatThrownBy(() -> service.update(project.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(developer.getId(), 1, List.of()), "x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não se muda");
    }

    // 5. DELETE de um perfil com 2 membros → os membros continuam, sem perfil.
    @Test
    void removingAProfileKeepsItsPeopleWithoutProfile() {
        ProjectTeamMember maria = member("Maria", inheritedAnalyst);
        ProjectTeamMember joao = member("João", inheritedAnalyst);

        service.delete(project.getId(), inheritedAnalyst.getId(), "cvt00983");
        reload();

        assertThat(em.find(ProjectProfile.class, inheritedAnalyst.getId())).isNull();
        assertThat(teamMembers.findByProjectId(project.getId()))
                .extracting(ProjectTeamMember::getName)
                .containsExactlyInAnyOrder(maria.getName(), joao.getName());
        assertThat(teamMembers.findByProjectId(project.getId()))
                .allMatch(member -> member.getProjectProfile() == null);
    }

    @Test
    void profileOfAnotherProjectIsNotFound() {
        Project other = new Project();
        other.setName("Outro");
        other.setStatus(ProjectStatus.PLANNED);
        em.persist(other);

        assertThatThrownBy(() -> service.update(other.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(null, 2, List.of()), "x"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(other.getId(), inheritedAnalyst.getId(), "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // 6. Projeto COMPLETED → POST/PUT/DELETE recusados com 409.
    @Test
    void closedProjectCannotChangeProfiles() {
        project.setStatus(ProjectStatus.COMPLETED);
        em.flush();

        assertConflict(() -> service.create(project.getId(),
                new ProjectProfileRequest(developer.getId(), 1, List.of()), "x"));
        assertConflict(() -> service.update(project.getId(), inheritedAnalyst.getId(),
                new ProjectProfileRequest(null, 2, List.of()), "x"));
        assertConflict(() -> service.delete(project.getId(), inheritedAnalyst.getId(), "cvt00983"));
    }

    // 7. Registos anteriores à coluna origin → DEMAND.
    @Test
    void recordsWithoutOriginAreReportedAsInheritedFromTheDemand() {
        em.getEntityManager().createNativeQuery("update project_profiles set origin = null").executeUpdate();
        reload();

        assertThat(service.list(project.getId())).extracting(ProjectProfileResponse::origin)
                .containsExactly(ProjectProfileOrigin.DEMAND);
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(409);
            assertThat(e.getMessage()).isEqualTo("Um projeto concluído ou cancelado não pode alterar os perfis.");
        });
    }

    private ProjectTeamMember member(String name, ProjectProfile profile) {
        ProjectTeamMember member = new ProjectTeamMember();
        member.setProject(project);
        member.setName(name);
        member.setType(MemberType.INTERNAL);
        member.setProjectProfile(profile);
        em.persist(member);
        em.flush();
        return member;
    }

    private void reload() {
        em.flush();
        em.clear();
    }

    private Profile profile(String name, boolean active) {
        Profile profile = new Profile();
        profile.setName(name);
        profile.setDirectionCode("DSI");
        profile.setAvailableCapacity(3);
        profile.setSimultaneousDemandCapacity(2);
        profile.setActive(active);
        return em.persist(profile);
    }

    private void phase(PhaseModel type, String code, String name, int sequence) {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setPhaseModel(type);
        phase.setCode(code);
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal("33.33"));
        em.persist(phase);
        type.getPhases().add(phase);
    }
}
