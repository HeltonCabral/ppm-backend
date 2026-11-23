package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.dto.CapacityPlanningDtos.*;
import cvt.cv.ppmbackend.dto.PersonCapacityDtos;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberRequest;
import cvt.cv.ppmbackend.entity.PersonCapacity;
import cvt.cv.ppmbackend.enums.CapacityReason;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberResponse;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.MemberType;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Capacidade no projeto, com base de dados real: os testes mínimos de CAPACIDADE-PROJETO-BACKEND.md. */
@DataJpaTest
class ProjectCapacityJpaTest {
    private static final int FEB = 0, MAR = 1, APR = 2;

    @Autowired private TestEntityManager em;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectTeamMemberRepository memberRepository;
    @Autowired private ProjectProfileRepository projectProfileRepository;
    @Autowired private ProjectPhaseRepository phaseRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ProjectEventRepository eventRepository;
    @Autowired private PersonCapacityRepository personCapacityRepository;

    private final AccessControlProperties properties = new AccessControlProperties();
    private final UserAccessService userAccess = mock(UserAccessService.class);
    private ProjectCapacityService capacity;
    private ProjectTeamService team;
    private Project portal;
    private Profile analyst;

    @BeforeEach
    void setUp() {
        ProjectGuard guard = new ProjectGuard(projects, properties, userAccess);
        ProjectEventService events = new ProjectEventService(eventRepository);
        capacity = new ProjectCapacityService(guard, memberRepository, projectProfileRepository, phaseRepository,
                personCapacityRepository);
        team = new ProjectTeamService(guard, memberRepository, new ProjectProfileService(projects,
                projectProfileRepository, memberRepository, profileRepository, events), events, capacity);

        analyst = new Profile();
        analyst.setName("Analista");
        analyst.setDirectionCode("DSI");
        analyst.setAvailableCapacity(2);
        analyst.setSimultaneousDemandCapacity(2);
        em.persist(analyst);

        portal = project("PRJ-PORTAL", ProjectStatus.IN_PROGRESS);
        phase(portal, "Análise", 1, "2027-02-01", "2027-02-28");
        phase(portal, "Desenvolvimento", 2, "2027-03-01", "2027-04-30");
        phase(portal, "Piloto", 3, "2027-05-01", "2027-05-31");
        em.flush();
    }

    // 1. Sem alocação guardada → 100% no período das fases do perfil, assumed.
    @Test
    void memberWithoutStoredAllocationUsesTheProfilePhases() {
        ProjectProfile profile = projectProfile(portal, 1, "Análise");
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, null, null, null, profile);
        reload();

        MemberCapacity row = memberRow(capacity.projectCapacity(portal.getId(), null, null), helton);

        assertThat(row.assumed()).isTrue();
        assertThat(row.allocationPercentage()).isEqualTo(100);
        assertThat(row.startDate()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(row.endDate()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(row.thisProject()).containsExactly(100, 0, 0, 0);
    }

    // 2. 50% de 15 a 31 de março → total 50 (pico) e assigned ≈ 0,27 FTE.
    @Test
    void partialMonthCountsThePeakForPeopleAndTheShareOfDaysForProfiles() {
        ProjectProfile profile = projectProfile(portal, 1, "Desenvolvimento");
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 50, "2027-03-15", "2027-03-31",
                profile);
        reload();

        ProjectCapacityResponse response = capacity.projectCapacity(portal.getId(), null, null);

        assertThat(response.months()).containsExactly("2027-02", "2027-03", "2027-04", "2027-05");
        MemberCapacity row = memberRow(response, helton);
        assertThat(row.assumed()).isFalse();
        assertThat(row.total().get(MAR)).isEqualTo(50);
        assertThat(response.profiles().get(0).assigned().get(MAR)).isEqualTo(0.27);
    }

    // 2b. 50% de 1 a 9 aqui e 80% o mês inteiro noutro projeto → 130 (pico), não a média.
    @Test
    void monthlyTotalIsTheDailyPeakNotTheAverage() {
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 50, "2027-03-01", "2027-03-09", null);
        Project other = project("PRJ-OUTRO", ProjectStatus.IN_PROGRESS);
        member(other, "CVT00983", MemberType.INTERNAL, 80, "2027-03-01", "2027-03-31", null);
        reload();

        MemberCapacity row = memberRow(capacity.projectCapacity(portal.getId(), null, null), helton);

        assertThat(row.total().get(MAR)).isEqualTo(130);
        assertThat(row.thisProject().get(MAR)).isEqualTo(50);
        assertThat(row.breakdown().get(MAR)).extracting(ProjectShare::projectCode, ProjectShare::percent,
                        ProjectShare::thisProject)
                .containsExactlyInAnyOrder(tuple("PRJ-PORTAL", 50, true), tuple("PRJ-OUTRO", 80, false));
    }

    // 3. 60% em dois projetos em curso → 120; se o outro estiver em BACKLOG, só conta este.
    @Test
    void onlyProjectsThatOccupyPeopleCount() {
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 60, "2027-03-01", "2027-03-31", null);
        Project other = project("PRJ-OUTRO", ProjectStatus.IN_PROGRESS);
        member(other, "cvt00983", MemberType.INTERNAL, 60, "2027-03-01", "2027-03-31", null);
        reload();

        MemberCapacity row = memberRow(capacity.projectCapacity(portal.getId(), null, null), helton);
        assertThat(row.total().get(MAR)).isEqualTo(120);
        assertThat(row.peak()).isEqualTo(120);

        Project reloaded = projects.findById(other.getId()).orElseThrow();
        reloaded.setStatus(ProjectStatus.BACKLOG);
        reload();
        assertThat(memberRow(capacity.projectCapacity(portal.getId(), null, null), helton).total().get(MAR))
                .isEqualTo(60);
    }

    // 4. Externo com o mesmo nome e código noutro projeto → não soma.
    @Test
    void externalMembersAreNotTheSamePerson() {
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 60, "2027-03-01", "2027-03-31", null);
        Project other = project("PRJ-OUTRO", ProjectStatus.IN_PROGRESS);
        member(other, "cvt00983", MemberType.EXTERNAL, 60, "2027-03-01", "2027-03-31", null);
        reload();

        assertThat(memberRow(capacity.projectCapacity(portal.getId(), null, null), helton).total().get(MAR))
                .isEqualTo(60);
    }

    // 5. Perfil com 2 pessoas nas fases de março e abril e uma pessoa a 100% → required 2/2, assigned 1/1.
    @Test
    void profileRequiredFollowsItsPhasesAndAssignedFollowsThePeople() {
        ProjectProfile profile = projectProfile(portal, 2, "Desenvolvimento");
        member(portal, "cvt00983", MemberType.INTERNAL, 100, null, null, profile);
        reload();

        ProfileCapacity row = capacity.projectCapacity(portal.getId(), null, null).profiles().get(0);

        assertThat(row.required()).containsExactly(0.0, 2.0, 2.0, 0.0);
        assertThat(row.assigned()).containsExactly(0.0, 1.0, 1.0, 0.0);
    }

    // 6. Dois projetos abertos com o mesmo perfil de capacidade (2 + 1) → portfolioLoad 3, available 2.
    @Test
    void portfolioLoadSumsTheSameCapacityProfileAcrossOpenProjects() {
        projectProfile(portal, 2, "Desenvolvimento");
        Project other = project("PRJ-OUTRO", ProjectStatus.PLANNED);
        projectProfile(other, 1, null);
        Project closed = project("PRJ-FECHADO", ProjectStatus.COMPLETED);
        projectProfile(closed, 5, null);
        reload();

        ProfileCapacity row = capacity.projectCapacity(portal.getId(), null, null).profiles().get(0);

        assertThat(row.portfolioLoad().get(MAR)).isEqualTo(3.0);
        assertThat(row.available()).isEqualTo(2);

        PortfolioProfile portfolio = capacity.portfolioProfiles(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))
                .profiles().get(0);
        assertThat(portfolio.load()).containsExactly(3.0);
        assertThat(portfolio.projects().get(0)).extracting(ProjectProfileShare::projectCode)
                .containsExactlyInAnyOrder("PRJ-PORTAL", "PRJ-OUTRO");
    }

    // 7. GET /capacity/people/{u}?excludeProjectId=X não conta o projeto X.
    @Test
    void personLoadCanExcludeTheProjectBeingEdited() {
        member(portal, "cvt00983", MemberType.INTERNAL, 50, "2027-03-01", "2027-03-31", null);
        Project other = project("PRJ-OUTRO", ProjectStatus.IN_PROGRESS);
        member(other, "cvt00983", MemberType.INTERNAL, 80, "2027-03-01", "2027-06-30", null);
        reload();
        LocalDate from = LocalDate.of(2027, 2, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);

        PersonLoadResponse all = capacity.person("CVT00983", from, to, null);
        PersonLoadResponse others = capacity.person("cvt00983", from, to, portal.getId());

        assertThat(all.load()).containsExactly(0, 130);
        assertThat(others.load()).containsExactly(0, 80);
        assertThat(others.assignments()).extracting(PersonAssignment::projectCode, PersonAssignment::allocationPercentage)
                .containsExactly(tuple("PRJ-OUTRO", 80));
    }

    // 8. PUT de um membro que passa a 130% → gravado, com avisos.
    @Test
    void savingAnOverallocationWarnsButDoesNotRefuse() {
        Project other = project("PRJ-OUTRO", ProjectStatus.IN_PROGRESS);
        member(other, "cvt00983", MemberType.INTERNAL, 80, "2027-03-01", "2027-06-30", null);
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 20, "2027-03-01", "2027-03-31", null);
        reload();

        ProjectTeamMemberResponse saved = team.update(portal.getId(), helton.getId(), new ProjectTeamMemberRequest(
                "cvt00983", null, "DSI", null, null, "Helton Andrade", null, null, MemberType.INTERNAL, null, 50,
                LocalDate.of(2027, 1, 15), LocalDate.of(2027, 3, 31)), "pmo");

        assertThat(saved.allocationPercentage()).isEqualTo(50);
        assertThat(saved.warnings()).containsExactly(
                "O período da alocação começa antes do início do projeto.",
                "Helton Andrade fica a 130% em mar/27 (PRJ-OUTRO 80%).");
    }

    @Test
    void fourConcurrentProjectsAreReturnedAndWarned() {
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 25,
                "2027-03-01", "2027-03-31", null);
        for (int i = 1; i <= 3; i++) {
            Project other = project("PRJ-CONC-" + i, ProjectStatus.IN_PROGRESS);
            member(other, "CVT00983", MemberType.INTERNAL, 25, "2027-03-01", "2027-03-31", null);
        }
        reload();

        MemberCapacity row = memberRow(capacity.projectCapacity(portal.getId(), null, null), helton);
        assertThat(row.concurrent().get(MAR)).isEqualTo(4);
        assertThat(row.maxConcurrent()).isEqualTo(3);

        ProjectTeamMemberResponse saved = team.update(portal.getId(), helton.getId(), new ProjectTeamMemberRequest(
                "cvt00983", null, "DSI", null, null, "Helton Andrade", null, null, MemberType.INTERNAL, null, 25,
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31)), "pmo");
        assertThat(saved.warnings()).contains("Helton Andrade fica em 4 projetos ao mesmo tempo em mar/27 (limite 3).");
    }

    // 9. Filtro de projetos ligado → um projeto não visível aparece como "Outro projeto", com a percentagem.
    @Test
    void projectsTheUserCannotSeeAreHiddenButStillCount() {
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 50, "2027-03-01", "2027-03-31", null);
        Project other = project("PRJ-SECRETO", ProjectStatus.IN_PROGRESS);
        member(other, "cvt00983", MemberType.INTERNAL, 80, "2027-03-01", "2027-03-31", null);
        Project managed = projects.findById(portal.getId()).orElseThrow();
        managed.setProjectManagerUsername("gestor");
        reload();
        properties.getProjectFilter().setEnabled(true);
        when(userAccess.current()).thenReturn(new UserAccess("gestor", Set.of(), List.of()));

        MemberCapacity row = memberRow(capacity.projectCapacity(portal.getId(), null, null), helton);

        assertThat(row.total().get(MAR)).isEqualTo(130);
        assertThat(row.breakdown().get(MAR)).extracting(ProjectShare::projectId, ProjectShare::projectName,
                        ProjectShare::percent)
                .contains(tuple(null, "Outro projeto", 80));
    }

    @Test
    void portfolioPeopleShowsEachPersonsPeakLoad() {
        member(portal, "cvt00983", MemberType.INTERNAL, 50, "2027-03-01", "2027-03-31", null);
        Project other = project("PRJ-OUTRO", ProjectStatus.ON_HOLD);
        member(other, "cvt00983", MemberType.INTERNAL, 70, "2027-03-10", "2027-04-30", null);
        member(other, "cvt00100", MemberType.INTERNAL, 30, "2027-04-01", "2027-04-30", null);
        reload();

        PortfolioPeopleResponse people = capacity.portfolioPeople(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 30),
                "dsi");

        assertThat(people.people()).extracting(PortfolioPerson::userName, PortfolioPerson::load, PortfolioPerson::peak)
                .containsExactlyInAnyOrder(tuple("cvt00983", List.of(120, 70), 120), tuple("cvt00100", List.of(0, 30), 30));
    }

    // 10. Capacidade permanente 60% e alocação a 70% → sobrealocada face à capacidade, com o motivo.
    @Test
    void overallocationIsMeasuredAgainstThePersonsCapacity() {
        personCapacity(60, CapacityReason.OPERATIONS, null, null);
        ProjectTeamMember helton = member(portal, "cvt00983", MemberType.INTERNAL, 70, "2027-03-01", "2027-03-31", null);
        reload();

        MemberCapacity row = memberRow(capacity.projectCapacity(portal.getId(), null, null), helton);

        assertThat(row.capacity().get(MAR)).isEqualTo(60);
        assertThat(row.total().get(MAR)).isGreaterThan(row.capacity().get(MAR));
        assertThat(row.capacityReason()).isEqualTo("funções operacionais");

        ProjectTeamMemberResponse saved = team.update(portal.getId(), helton.getId(), new ProjectTeamMemberRequest(
                "cvt00983", null, "DSI", null, null, "Helton Andrade", null, null, MemberType.INTERNAL, null, 70,
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31)), "pmo");
        assertThat(saved.warnings()).containsExactly(
                "Helton Andrade fica a 70% em mar/27, acima da sua capacidade de 60% (funções operacionais).");
    }

    // 11. Permanente 60% e ausência a 0% de 10 a 14 de março → março 0, abril 60.
    @Test
    void aLeaveMakesTheWholeMonthWorthItsLowestDay() {
        personCapacity(60, CapacityReason.OPERATIONS, null, null);
        personCapacity(0, CapacityReason.LEAVE, "2027-03-10", "2027-03-14");
        reload();

        PersonLoadResponse load = capacity.person("cvt00983", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 30), null);

        assertThat(load.capacity()).containsExactly(0, 60);
        assertThat(load.capacityReason()).isEqualTo("funções operacionais");
    }

    // 12. Segundo registo permanente → 409; LEAVE sem datas → 400; escrita sem permissão (filtro ligado) → 403.
    @Test
    void personCapacityValidations() {
        PersonCapacityService service = new PersonCapacityService(personCapacityRepository,
                new ProjectGuard(projects, properties, userAccess));
        service.create(new PersonCapacityDtos.Request("CVT00983", "Helton Andrade", 60, CapacityReason.PART_TIME,
                null, null, null), "pmo");

        assertThatThrownBy(() -> service.create(new PersonCapacityDtos.Request("cvt00983", "Helton Andrade", 50,
                CapacityReason.MANAGEMENT, null, null, null), "pmo"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getMessage()).isEqualTo("Helton Andrade já tem uma capacidade permanente.");
                });
        assertThatThrownBy(() -> service.create(new PersonCapacityDtos.Request("cvt00983", null, 0,
                CapacityReason.LEAVE, LocalDate.of(2027, 3, 10), null, null), "pmo"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(new PersonCapacityDtos.Request("cvt00983", null, 100,
                CapacityReason.OTHER, null, LocalDate.of(2027, 3, 1), null), "pmo"))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.create(new PersonCapacityDtos.Request("cvt00983", null, 0, CapacityReason.TRAINING,
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 5), null), "pmo").userName()).isEqualTo("cvt00983");
        assertThat(service.create(new PersonCapacityDtos.Request("cvt00101", "Pessoa a tempo inteiro", 100, 2,
                CapacityReason.OTHER, null, null, null), "pmo").maxConcurrent()).isEqualTo(2);
        assertThat(service.list("CVT00983")).hasSize(2);

        properties.getProjectFilter().setEnabled(true);
        when(userAccess.current()).thenReturn(new UserAccess("cvt00983", Set.of(), List.of()));
        assertThatThrownBy(() -> service.create(new PersonCapacityDtos.Request("cvt00100", null, 50,
                CapacityReason.PART_TIME, null, null, null), "cvt00983"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
    }

    // ----- Apoio -----

    private void personCapacity(int percentage, CapacityReason reason, String from, String until) {
        PersonCapacity capacity = new PersonCapacity();
        capacity.setUserName("cvt00983");
        capacity.setCapacityPercentage(percentage);
        capacity.setReason(reason);
        capacity.setValidFrom(from == null ? null : LocalDate.parse(from));
        capacity.setValidUntil(until == null ? null : LocalDate.parse(until));
        em.persist(capacity);
    }

    private static MemberCapacity memberRow(ProjectCapacityResponse response, ProjectTeamMember member) {
        return response.members().stream().filter(m -> m.memberId().equals(member.getId())).findFirst().orElseThrow();
    }

    private Project project(String code, ProjectStatus status) {
        Project project = new Project();
        project.setCode(code);
        project.setName(code);
        project.setStatus(status);
        ProjectExecution execution = new ProjectExecution();
        execution.setPlannedStartDate(LocalDate.of(2027, 2, 1));
        execution.setPlannedEndDate(LocalDate.of(2027, 5, 31));
        project.attachExecution(execution);
        return em.persist(project);
    }

    private void phase(Project project, String name, int sequence, String start, String end) {
        ProjectPhase phase = new ProjectPhase();
        phase.setProject(project);
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setWeight(BigDecimal.TEN);
        phase.setPlannedStartDate(LocalDate.parse(start));
        phase.setPlannedEndDate(LocalDate.parse(end));
        em.persist(phase);
    }

    private ProjectProfile projectProfile(Project project, int quantity, String phaseNames) {
        ProjectProfile profile = new ProjectProfile();
        profile.setProject(project);
        profile.setProfile(analyst);
        profile.setProfileName("Analista");
        profile.setDirectionCode("DSI");
        profile.setRequiredQuantity(quantity);
        profile.setPhaseNames(phaseNames);
        // Liga às fases do projeto com esses nomes, como a migração.
        em.flush();
        phaseRepository.findByProjectIdOrderBySequenceAsc(project.getId()).stream()
                .filter(p -> ProjectProfileService.splitPhases(phaseNames).contains(p.getName()))
                .forEach(p -> profile.getPhases().add(p));
        return em.persist(profile);
    }

    private ProjectTeamMember member(Project project, String code, MemberType type, Integer allocation, String start,
            String end, ProjectProfile profile) {
        ProjectTeamMember member = new ProjectTeamMember();
        member.setProject(project);
        member.setCode(code);
        member.setName("Helton Andrade");
        member.setDirectionCode("DSI");
        member.setType(type);
        member.setAllocationPercentage(allocation);
        member.setStartDate(start == null ? null : LocalDate.parse(start));
        member.setEndDate(end == null ? null : LocalDate.parse(end));
        member.setProjectProfile(profile);
        return em.persist(member);
    }

    private void reload() {
        em.flush();
        em.clear();
    }
}
