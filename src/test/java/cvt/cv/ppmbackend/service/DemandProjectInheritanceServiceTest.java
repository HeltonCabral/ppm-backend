package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandArea;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirectionArea;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.DemandSuccessCriterion;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectArea;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectSuccessCriterion;
import cvt.cv.ppmbackend.entity.Supplier;
import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.Priority;
import cvt.cv.ppmbackend.repository.DemandAreaRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionAreaRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.DemandSuccessCriterionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandProjectInheritanceServiceTest {

    @Mock private DemandAreaRepository demandAreas;
    @Mock private DemandParticipatingDirectionRepository participatingDirections;
    @Mock private DemandParticipatingDirectionAreaRepository participatingDirectionAreas;
    @Mock private DemandSuccessCriterionRepository successCriteria;
    @Mock private DemandProfileRequirementRepository profileRequirements;
    @Mock private DemandResourceDerivationService resourceDerivation;

    private DemandProjectInheritanceService service;
    private Demand demand;

    @BeforeEach
    void setUp() {
        service = new DemandProjectInheritanceService(demandAreas, participatingDirections,
                participatingDirectionAreas, successCriteria,
                new DemandRequirementsResolver(resourceDerivation, profileRequirements));
        demand = demand();

        DemandParticipatingDirection dce = direction("DCE", "Direção Comercial", DirectionParticipationType.IMPLEMANTATION,
                null);
        DemandParticipatingDirection dom = direction("DOM", "Direção de Operações", DirectionParticipationType.INTERVENIENT,
                "CRN");
        lenient().when(demandAreas.findByDemandIdOrderByCreatedAtAsc(demand.getId())).thenReturn(List.of(
                demandArea("GDS", AreaRole.RESPONSIBLE), demandArea("PIG", AreaRole.INTERVENIENT)));
        lenient().when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(demand.getId()))
                .thenReturn(List.of(dce, dom));
        lenient().when(participatingDirectionAreas.findByParticipatingDirectionIdOrderByCreatedAtAsc(dce.getId()))
                .thenReturn(List.of(pdArea(dce, "PPV", AreaRole.RESPONSIBLE), pdArea(dce, "CGC", AreaRole.INTERVENIENT)));
        lenient().when(participatingDirectionAreas.findByParticipatingDirectionIdOrderByCreatedAtAsc(dom.getId()))
                .thenReturn(List.of());
        lenient().when(successCriteria.findByDemandIdOrderByDisplayOrderAsc(demand.getId())).thenReturn(List.of(
                criterion("Adesão de 80% dos clientes", 1), criterion("Tempo de resposta < 2s", 2)));
    }

    @Test
    void projectOfOwnerDirectionKeepsItsRolesAndOtherDirectionsAreIntervenient() {
        Project project = project("DSI");

        service.inherit(project, demand);

        assertThat(project.getAreas())
                .extracting(ProjectArea::getDirectionCode, ProjectArea::getAreaCode, ProjectArea::getRole)
                .containsExactly(
                        tuple("DSI", "GDS", AreaRole.RESPONSIBLE),
                        tuple("DSI", "PIG", AreaRole.INTERVENIENT),
                        tuple("DCE", "PPV", AreaRole.INTERVENIENT),
                        tuple("DCE", "CGC", AreaRole.INTERVENIENT),
                        tuple("DOM", "CRN", AreaRole.INTERVENIENT));
        assertThat(project.getAreas()).allMatch(area -> area.getProject() == project);
    }

    @Test
    void projectOfParticipatingDirectionTakesItsOwnResponsibleArea() {
        Project project = project("DCE");

        service.inherit(project, demand);

        assertThat(project.getAreas())
                .filteredOn(area -> area.getRole() == AreaRole.RESPONSIBLE)
                .extracting(ProjectArea::getDirectionCode, ProjectArea::getAreaCode)
                .containsExactly(tuple("DCE", "PPV"));
        assertThat(project.getAreas()).hasSize(5);
    }

    @Test
    void copiesDemandDataSuccessCriteriaAndSuppliers() {
        Project project = project("DSI");

        service.inherit(project, demand);

        assertThat(project.getSponsor()).isEqualTo("Administração");
        assertThat(project.getRequester()).isEqualTo("cvt00983");
        assertThat(project.getProblemStatement()).isEqualTo("Processo manual");
        assertThat(project.getProposedResponse()).isEqualTo("Automatizar");
        assertThat(project.getRisksIdentified()).isEqualTo("Atraso do fornecedor");
        assertThat(project.getDependenciesIdentified()).isEqualTo("Migração do CRM");
        assertThat(project.getImpactedSystem()).isEqualTo("CRM");
        assertThat(project.getBusinessArea()).isEqualTo("Gestão e Desenvolvimento");
        assertThat(project.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(project.getDemandNature()).isEqualTo(DemandNature.REGULATORY);
        assertThat(project.getComplexity()).isEqualTo(DemandComplexity.HIGH);
        assertThat(project.getMandatoryDeadline()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(project.getPhaseModel()).isSameAs(demand.getType());
        assertThat(project.getTypeFields()).isEqualTo(Map.of("participantes", 25));
        assertThat(project.getSuppliers()).extracting(Supplier::getName).containsExactly("Fornecedor A");
        assertThat(project.getSuccessCriteria())
                .extracting(ProjectSuccessCriterion::getDescription, ProjectSuccessCriterion::getDisplayOrder)
                .containsExactly(tuple("Adesão de 80% dos clientes", 1), tuple("Tempo de resposta < 2s", 2));
    }

    @Test
    void doesNotOverwriteValuesAlreadyChosenForTheProject() {
        Project project = project("DSI");
        project.setPriority(Priority.LOW);
        project.setImpactedSystem("ERP");

        service.inherit(project, demand);

        assertThat(project.getPriority()).isEqualTo(Priority.LOW);
        assertThat(project.getImpactedSystem()).isEqualTo("ERP");
    }

    @Test
    void profilesNeedPeopleInParallelNotTheSumOfPhases() {
        PhaseDefinition dev = phase("Desenvolvimento", 2);
        PhaseDefinition tests = phase("Testes", 3);
        PhaseDefinition analysis = phase("Análise", 1);
        Profile analyst = profile("Analista", "DSI");
        Profile developer = profile("Programador", "DSI");
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId())).thenReturn(List.of(
                requirement(analyst, 1, dev), requirement(analyst, 1, tests, analysis),
                requirement(developer, 2, dev), requirement(developer, 1, dev)));
        Project project = project("DSI");

        service.inherit(project, demand);

        assertThat(project.getProfiles())
                .extracting(ProjectProfile::getProfileName, ProjectProfile::getRequiredQuantity,
                        ProjectProfile::getPhaseNames)
                .containsExactly(
                        tuple("Analista", 1, "Análise; Desenvolvimento; Testes"),
                        tuple("Programador", 3, "Desenvolvimento"));
        assertThat(project.getProfiles()).allMatch(p -> p.getProject() == project);
    }

    @Test
    void usesDerivedResourcesWhenTheTypeOrCustomPlanningDefinesThem() {
        Profile architect = profile("Arquiteto", "DSI");
        when(resourceDerivation.hasDerivedResources(demand)).thenReturn(true);
        when(resourceDerivation.deriveRequirements(demand))
                .thenReturn(List.of(requirement(architect, 1, phase("Desenho", 1))));
        Project project = project("DSI");

        service.inherit(project, demand);

        assertThat(project.getProfiles()).extracting(ProjectProfile::getProfileName).containsExactly("Arquiteto");
        verify(profileRequirements, never()).findByDemand_IdOrderByProfile_NameAsc(any());
    }

    @Test
    void programSplitGivesEachProjectTheProfilesOfItsDirection() {
        // DSI (dona) e DCE implementam; DOM só intervém → os perfis DOM e sem direção ficam no projeto DSI.
        PhaseDefinition dev = phase("Desenvolvimento", 1);
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId())).thenReturn(List.of(
                requirement(profile("Programador", "DSI"), 1, dev),
                requirement(profile("Gestor Comercial", "DCE"), 1, dev),
                requirement(profile("Técnico de Rede", "DOM"), 1, dev),
                requirement(profile("Consultor", null), 1, dev)));
        Program program = new Program();

        Project dsiProject = project("DSI");
        dsiProject.setProgram(program);
        service.inherit(dsiProject, demand);
        Project dceProject = project("DCE");
        dceProject.setProgram(program);
        service.inherit(dceProject, demand);

        assertThat(dsiProject.getProfiles()).extracting(ProjectProfile::getProfileName)
                .containsExactly("Programador", "Técnico de Rede", "Consultor");
        assertThat(dceProject.getProfiles()).extracting(ProjectProfile::getProfileName)
                .containsExactly("Gestor Comercial");
    }

    @Test
    void singleProjectGetsAllProfiles() {
        PhaseDefinition dev = phase("Desenvolvimento", 1);
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId())).thenReturn(List.of(
                requirement(profile("Programador", "DSI"), 1, dev),
                requirement(profile("Gestor Comercial", "DCE"), 1, dev)));
        Project project = project("DSI");

        service.inherit(project, demand);

        assertThat(project.getProfiles()).hasSize(2);
    }

    @Test
    void unknownUrgencyLeavesPriorityEmptyInsteadOfFailing() {
        demand.setUrgency("URGENTISSIMO");
        Project project = project("DSI");

        service.inherit(project, demand);

        assertThat(project.getPriority()).isNull();
    }

    private Demand demand() {
        Demand d = new Demand();
        d.setId(UUID.randomUUID());
        d.setCode("DEM-001");
        d.setTitle("Portal de clientes");
        d.setDirectionCode("DSI");
        d.setDirectionName("Direção de Sistemas de Informação");
        d.setDirectionParticipationType(DirectionParticipationType.IMPLEMANTATION);
        d.setAreaCode("GDS");
        d.setAreaName("Gestão e Desenvolvimento");
        d.setSponsor("Administração");
        d.setRequester("cvt00983");
        d.setProblemStatement("Processo manual");
        d.setProposedResponse("Automatizar");
        d.setRisksIdentified("Atraso do fornecedor");
        d.setDependenciesIdentified("Migração do CRM");
        d.setImpactedSystem("CRM");
        d.setUrgency("HIGH");
        d.setNature(DemandNature.REGULATORY);
        d.setComplexity(DemandComplexity.HIGH);
        d.setMandatoryDeadline(LocalDate.of(2027, 6, 30));
        d.setTypeFields(new java.util.LinkedHashMap<>(Map.of("participantes", 25)));
        PhaseModel type = new PhaseModel();
        type.setId(UUID.randomUUID());
        type.setName("Formação");
        d.setType(type);
        Supplier supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Fornecedor A");
        d.getSuppliers().add(supplier);
        return d;
    }

    private static Project project(String directionCode) {
        Project project = new Project();
        project.setDirectionCode(directionCode);
        project.setDirectionName(directionCode);
        return project;
    }

    private DemandArea demandArea(String code, AreaRole role) {
        DemandArea area = new DemandArea();
        area.setDemand(demand);
        area.setAreaCode(code);
        area.setAreaName(code);
        area.setRole(role);
        return area;
    }

    private DemandParticipatingDirection direction(String code, String name, DirectionParticipationType type,
            String areaCode) {
        DemandParticipatingDirection direction = new DemandParticipatingDirection();
        direction.setId(UUID.randomUUID());
        direction.setDemand(demand);
        direction.setDirectionCode(code);
        direction.setDirectionName(name);
        direction.setParticipationType(type);
        direction.setAreaCode(areaCode);
        direction.setAreaName(areaCode);
        return direction;
    }

    private static DemandParticipatingDirectionArea pdArea(DemandParticipatingDirection direction, String code,
            AreaRole role) {
        DemandParticipatingDirectionArea area = new DemandParticipatingDirectionArea();
        area.setParticipatingDirection(direction);
        area.setAreaCode(code);
        area.setAreaName(code);
        area.setRole(role);
        return area;
    }

    private static PhaseDefinition phase(String name, int sequence) {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setId(UUID.randomUUID());
        phase.setName(name);
        phase.setSequence(sequence);
        return phase;
    }

    private static Profile profile(String name, String directionCode) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);
        profile.setDirectionCode(directionCode);
        return profile;
    }

    private DemandProfileRequirement requirement(Profile profile, int quantity, PhaseDefinition... phases) {
        DemandProfileRequirement requirement = new DemandProfileRequirement();
        requirement.setDemand(demand);
        requirement.setProfile(profile);
        requirement.setRequiredQuantity(quantity);
        requirement.setPhases(new java.util.LinkedHashSet<>(List.of(phases)));
        return requirement;
    }

    private DemandSuccessCriterion criterion(String description, int order) {
        DemandSuccessCriterion criterion = new DemandSuccessCriterion();
        criterion.setDemand(demand);
        criterion.setDescription(description);
        criterion.setDisplayOrder(order);
        return criterion;
    }
}
