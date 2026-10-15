package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectProfileResponse;
import cvt.cv.ppmbackend.dto.ProjectResponse;
import cvt.cv.ppmbackend.dto.ProjectResponse.AreaResponse;
import cvt.cv.ppmbackend.dto.ProjectTeamMemberResponse;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.MemberType;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandArea;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirectionArea;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandSuccessCriterion;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Conversão real Demanda → Projeto, até à base de dados, com áreas, critérios e campos do Tipo. */
@DataJpaTest
class DemandProjectInheritanceJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectExecutionRankHistoryRepository rankHistory;
    @Autowired private LookupValueRepository lookups;
    @Autowired private SupplierRepository suppliers;
    @Autowired private ProgramRepository programs;
    @Autowired private StrategicPlanRepository strategicPlans;
    @Autowired private OperationalPlanRepository operationalPlans;
    @Autowired private StrategicPillarRepository strategicPillars;
    @Autowired private StrategicObjectiveRepository strategicObjectives;
    @Autowired private DemandAreaRepository demandAreas;
    @Autowired private DemandParticipatingDirectionRepository participatingDirections;
    @Autowired private DemandParticipatingDirectionAreaRepository participatingDirectionAreas;
    @Autowired private DemandSuccessCriterionRepository successCriteria;
    @Autowired private DemandProfileRequirementRepository profileRequirements;
    @Autowired private ProjectProfileRepository projectProfiles;
    @Autowired private ProjectTeamMemberRepository teamMembers;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ProjectEventRepository eventRepository;
    @Autowired private ProjectPhaseRepository phaseRepository;
    @Autowired private ProjectMilestoneRepository milestoneRepository;
    @Autowired private ProjectClosureRepository closureRepository;
    @Autowired private ProjectLessonLearnedRepository lessonRepository;
    @Autowired private PhaseModelRepository phaseModels;

    @Test
    void approvedDemandBecomesProjectWithAreasCriteriaAndDemandData() {
        Demand demand = persistDemand();
        ProjectEventService events = new ProjectEventService(eventRepository);
        ProjectGuard guard = new ProjectGuard(projects, new cvt.cv.ppmbackend.config.AccessControlProperties(),
                null);
        ProjectService service = new ProjectService(projects, rankHistory, lookups, suppliers, programs,
                strategicPlans, operationalPlans, strategicPillars, strategicObjectives,
                new DemandProjectInheritanceService(demandAreas, participatingDirections,
                        participatingDirectionAreas, successCriteria, new DemandRequirementsResolver(
                        new DemandResourceDerivationService(participatingDirections), profileRequirements)),
                new ProjectPhaseService(phaseRepository, projects, guard, events, milestoneRepository), events,
                new ProjectClosureService(closureRepository, lessonRepository, guard), guard, phaseModels);

        Project project = new Project();
        project.setName(demand.getTitle());
        project.setDirectionCode("DSI");
        project.setDirectionName("Direção de Sistemas de Informação");
        Project saved = service.saveFromDemand(project, demand, new DemandPlanningCycle(),
                null, null, null, null, null, "cvt00983");
        em.flush();
        em.clear();

        ProjectResponse response = ProjectResponse.detailed(em.find(Project.class, saved.getId()));

        assertThat(response.areas())
                .extracting(AreaResponse::directionCode, AreaResponse::areaCode, AreaResponse::role)
                .containsExactly(
                        tuple("DSI", "GDS", AreaRole.RESPONSIBLE),
                        tuple("DCE", "PPV", AreaRole.INTERVENIENT),
                        tuple("DSI", "PIG", AreaRole.INTERVENIENT));
        assertThat(response.successCriteria()).containsExactly("Adesão de 80% dos clientes");
        assertThat(response.demandData().sponsor()).isEqualTo("Administração");
        assertThat(response.demandData().problemStatement()).isEqualTo("Processo manual");
        assertThat(response.demandData().typeFields()).isEqualTo(Map.of("participantes", 25));
        assertThat(response.execution().plannedStartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(response.execution().plannedEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));

        // Perfis herdados: a equipa é preenchida dentro de cada perfil.
        ProjectProfileService profileService = new ProjectProfileService(projects, projectProfiles, teamMembers, profileRepository,
                events);
        ProjectProfileResponse analyst = profileService.list(saved.getId()).get(0);
        assertThat(analyst.profileName()).isEqualTo("Analista Funcional");
        assertThat(analyst.requiredQuantity()).isEqualTo(2);
        assertThat(analyst.missing()).isEqualTo(2);

        ProjectTeamMember member = new ProjectTeamMember();
        member.setProject(em.find(Project.class, saved.getId()));
        member.setName("Maria Silva");
        member.setType(MemberType.INTERNAL);
        ProjectProfileService.applyProfileDefaults(member,
                profileService.resolveForMember(saved.getId(), analyst.id()));
        em.persist(member);
        em.flush();
        em.clear();

        ProjectProfileResponse filled = profileService.list(saved.getId()).get(0);
        assertThat(filled.assignedCount()).isEqualTo(1);
        assertThat(filled.missing()).isEqualTo(1);
        assertThat(filled.members()).extracting(ProjectTeamMemberResponse::name, ProjectTeamMemberResponse::role,
                ProjectTeamMemberResponse::directionCode)
                .containsExactly(tuple("Maria Silva", "Analista Funcional", "DSI"));
        assertThat(ProjectResponse.detailed(em.find(Project.class, saved.getId())).profiles())
                .extracting(ProjectResponse.ProfileSummary::profileName, ProjectResponse.ProfileSummary::assignedCount)
                .containsExactly(tuple("Analista Funcional", 1L));
    }

    private Demand persistDemand() {
        Demand demand = new Demand();
        demand.setCode("DEM-INH");
        demand.setTitle("Portal de clientes");
        demand.setProblemStatement("Processo manual");
        demand.setDesiredOutcome("Clientes autónomos");
        demand.setDirectionCode("DSI");
        demand.setDirectionName("Direção de Sistemas de Informação");
        demand.setAreaCode("GDS");
        demand.setSponsor("Administração");
        demand.setPlannedStartDate(LocalDate.of(2027, 1, 1));
        demand.setDesiredDate(LocalDate.of(2027, 6, 30));
        demand.setTypeFields(new LinkedHashMap<>(Map.of("participantes", 25)));
        em.persist(demand);

        em.persist(demandArea(demand, "GDS", AreaRole.RESPONSIBLE));
        em.persist(demandArea(demand, "PIG", AreaRole.INTERVENIENT));

        DemandParticipatingDirection dce = new DemandParticipatingDirection();
        dce.setDemand(demand);
        dce.setDirectionCode("DCE");
        dce.setDirectionName("Direção Comercial");
        dce.setParticipationType(DirectionParticipationType.INTERVENIENT);
        em.persist(dce);
        DemandParticipatingDirectionArea ppv = new DemandParticipatingDirectionArea();
        ppv.setParticipatingDirection(dce);
        ppv.setAreaCode("PPV");
        ppv.setRole(AreaRole.RESPONSIBLE);
        em.persist(ppv);

        Profile analyst = new Profile();
        analyst.setName("Analista Funcional");
        analyst.setDirectionCode("DSI");
        analyst.setAvailableCapacity(5);
        analyst.setSimultaneousDemandCapacity(2);
        em.persist(analyst);
        DemandProfileRequirement requirement = new DemandProfileRequirement();
        requirement.setDemand(demand);
        requirement.setProfile(analyst);
        requirement.setRequiredQuantity(2);
        em.persist(requirement);

        DemandSuccessCriterion criterion = new DemandSuccessCriterion();
        criterion.setDemand(demand);
        criterion.setDescription("Adesão de 80% dos clientes");
        criterion.setDisplayOrder(1);
        em.persist(criterion);
        em.flush();
        return demand;
    }

    private static DemandArea demandArea(Demand demand, String code, AreaRole role) {
        DemandArea area = new DemandArea();
        area.setDemand(demand);
        area.setAreaCode(code);
        area.setRole(role);
        return area;
    }
}
