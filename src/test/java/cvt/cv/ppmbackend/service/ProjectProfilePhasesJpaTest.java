package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhasePlanItem;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.PhasePlanRequest;
import cvt.cv.ppmbackend.dto.ProjectProfileRequest;
import cvt.cv.ppmbackend.dto.ProjectProfileResponse;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberRequest;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectEvent;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.enums.MemberType;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Perfis ligados às fases do projeto por id (secção 7 de PERFIS-PROJETO-BACKEND.md). */
@DataJpaTest
class ProjectProfilePhasesJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectPhaseRepository phaseRepository;
    @Autowired private ProjectMilestoneRepository milestoneRepository;
    @Autowired private ProjectProfileRepository projectProfileRepository;
    @Autowired private ProjectTeamMemberRepository memberRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ProjectEventRepository eventRepository;
    @Autowired private PersonCapacityRepository personCapacityRepository;

    private ProjectPhaseService phases;
    private ProjectProfileService profiles;
    private ProjectTeamService team;
    private Project project;
    private ProjectProfile analyst;

    @BeforeEach
    void setUp() {
        ProjectEventService events = new ProjectEventService(eventRepository);
        ProjectGuard guard = new ProjectGuard(projects, new AccessControlProperties(), null);
        phases = new ProjectPhaseService(phaseRepository, projects, guard, events, milestoneRepository);
        profiles = new ProjectProfileService(projects, projectProfileRepository, memberRepository, profileRepository,
                events);
        ProjectCapacityService capacity = new ProjectCapacityService(guard, memberRepository, projectProfileRepository,
                phaseRepository, personCapacityRepository);
        team = new ProjectTeamService(guard, memberRepository, profiles, events, capacity);

        project = project("PRJ-A");
        analyst = profile(project, "Analista Funcional", 2, "Análise; Testes");
        reload();
    }

    // 8. phaseIds de uma fase criada no projeto → aceite; GET devolve phaseIds e o nome atual.
    @Test
    void profileCanUseAPhaseCreatedInTheProject() {
        UUID acceptance = addAcceptancePhase();

        ProjectProfileResponse updated = profiles.update(project.getId(), analyst.getId(),
                new ProjectProfileRequest(null, 2, List.of("Testes de aceitação"), List.of(acceptance)), "x");
        reload();

        assertThat(updated.phaseIds()).containsExactly(acceptance);
        ProjectProfileResponse listed = profiles.list(project.getId()).get(0);
        assertThat(listed.phaseIds()).containsExactly(acceptance);
        assertThat(listed.phases()).containsExactly("Testes de aceitação");
    }

    // 9. Renomear a fase → o perfil continua ligado e mostra o novo nome.
    @Test
    void renamingAPhaseKeepsTheLink() {
        UUID acceptance = addAcceptancePhase();
        profiles.update(project.getId(), analyst.getId(),
                new ProjectProfileRequest(null, 2, null, List.of(acceptance)), "x");
        reload();

        phases.replacePlan(project.getId(), new PhasePlanRequest(List.of(
                new PhasePlanItem(phase("Análise"), "Análise", new BigDecimal("40")),
                new PhasePlanItem(phase("Testes"), "Testes", new BigDecimal("40")),
                new PhasePlanItem(acceptance, "Aceitação do fornecedor", new BigDecimal("20"))), false, null), "x");
        reload();

        ProjectProfileResponse listed = profiles.list(project.getId()).get(0);
        assertThat(listed.phaseIds()).containsExactly(acceptance);
        assertThat(listed.phases()).containsExactly("Aceitação do fornecedor");
    }

    // 10. Remover a única fase de um perfil → "todo o projeto", com evento.
    @Test
    void removingTheOnlyPhaseOfAProfileMakesItWholeProject() {
        UUID testes = phase("Testes");
        profiles.update(project.getId(), analyst.getId(), new ProjectProfileRequest(null, 2, null, List.of(testes)), "x");
        reload();

        phases.replacePlan(project.getId(), new PhasePlanRequest(List.of(
                new PhasePlanItem(phase("Análise"), "Análise", new BigDecimal("100"))), true, null), "x");
        reload();

        ProjectProfileResponse listed = profiles.list(project.getId()).get(0);
        assertThat(listed.phaseIds()).isEmpty();
        assertThat(listed.phases()).isEmpty();
        assertThat(eventRepository.findByProjectIdOrderByAtDesc(project.getId())).extracting(ProjectEvent::getType)
                .contains(ProjectEventType.PROFILE_PHASES_CLEARED);
    }

    // 11. phaseIds com a fase de outro projeto → 400. Nomes desconhecidos no projeto → 400.
    @Test
    void phasesMustBelongToTheProject() {
        Project other = project("PRJ-B");
        reload();
        UUID foreign = phaseRepository.findByProjectIdOrderBySequenceAsc(other.getId()).get(0).getId();

        assertThatThrownBy(() -> profiles.update(project.getId(), analyst.getId(),
                new ProjectProfileRequest(null, 2, null, List.of(foreign)), "x"))
                .isInstanceOf(BadRequestException.class).hasMessage("A fase indicada não pertence ao projeto.");
        assertThatThrownBy(() -> profiles.update(project.getId(), analyst.getId(),
                new ProjectProfileRequest(null, 2, List.of("Produção"), null), "x"))
                .isInstanceOf(BadRequestException.class);
        // Sem phaseIds, os nomes contam contra as fases do projeto (sem distinguir maiúsculas nem espaços).
        assertThat(profiles.update(project.getId(), analyst.getId(),
                new ProjectProfileRequest(null, 2, List.of(" testes "), null), "x").phases()).containsExactly("Testes");
    }

    // 12. Migração: phase_names "Análise; Testes" → ligado às duas fases.
    @Test
    void migrationLinksProfilesByPhaseName() {
        assertThat(phases.linkExistingProfiles()).isEqualTo(1);
        reload();

        ProjectProfileResponse listed = profiles.list(project.getId()).get(0);
        assertThat(listed.phaseIds()).containsExactly(phase("Análise"), phase("Testes"));
        assertThat(phases.linkExistingProfiles()).isZero();
    }

    // 13. Membro com o perfil de outro projeto → 400; perfil completo → aviso, mas aceita.
    @Test
    void membersAndProfiles() {
        Project other = project("PRJ-B");
        ProjectProfile foreign = profile(other, "Programador", 1, null);
        reload();
        assertThatThrownBy(() -> team.create(project.getId(), member("Ana Silva", foreign.getId()), "x"))
                .isInstanceOf(BadRequestException.class);

        team.create(project.getId(), member("Rui Lopes", analyst.getId()), "x");
        team.create(project.getId(), member("Maria Lopes", analyst.getId()), "x");
        List<String> warnings = team.create(project.getId(), member("Ana Silva", analyst.getId()), "x").warnings();

        assertThat(warnings).contains("Analista Funcional já tem as 2 pessoas previstas; Ana Silva fica a mais.");
    }

    // ----- Apoio -----

    private UUID addAcceptancePhase() {
        phases.replacePlan(project.getId(), new PhasePlanRequest(List.of(
                new PhasePlanItem(phase("Análise"), "Análise", new BigDecimal("40")),
                new PhasePlanItem(phase("Testes"), "Testes", new BigDecimal("40")),
                new PhasePlanItem(null, "Testes de aceitação", new BigDecimal("20"))), true, null), "x");
        reload();
        return phase("Testes de aceitação");
    }

    private UUID phase(String name) {
        return phaseRepository.findByProjectIdOrderBySequenceAsc(project.getId()).stream()
                .filter(p -> p.getName().equals(name)).map(ProjectPhase::getId).findFirst().orElseThrow();
    }

    private Project project(String code) {
        Project p = new Project();
        p.setCode(code);
        p.setName(code);
        p.setStatus(ProjectStatus.PLANNED);
        ProjectExecution execution = new ProjectExecution();
        execution.setPlannedStartDate(LocalDate.of(2027, 1, 1));
        execution.setPlannedEndDate(LocalDate.of(2027, 4, 10));
        p.attachExecution(execution);
        phases.createPhases(p, List.of(
                new ProjectPhasePlanner.PhaseSeed("Análise", 1, new BigDecimal("50")),
                new ProjectPhasePlanner.PhaseSeed("Testes", 2, new BigDecimal("50"))), "x");
        return projects.save(p);
    }

    private ProjectProfile profile(Project p, String name, int quantity, String phaseNames) {
        Profile capacity = new Profile();
        capacity.setName(name);
        capacity.setAvailableCapacity(3);
        capacity.setSimultaneousDemandCapacity(2);
        em.persist(capacity);
        ProjectProfile profile = new ProjectProfile();
        profile.setProject(p);
        profile.setProfile(capacity);
        profile.setProfileName(name);
        profile.setRequiredQuantity(quantity);
        profile.setPhaseNames(phaseNames);
        return em.persist(profile);
    }

    private static ProjectTeamMemberRequest member(String name, UUID projectProfileId) {
        return new ProjectTeamMemberRequest(null, null, null, null, null, name, null, null, MemberType.INTERNAL,
                projectProfileId, null, null, null);
    }

    private void reload() {
        em.flush();
        em.clear();
    }
}
