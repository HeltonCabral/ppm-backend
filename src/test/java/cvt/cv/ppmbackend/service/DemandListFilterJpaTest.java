package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.DemandResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.repository.DemandAreaRepository;
import cvt.cv.ppmbackend.repository.DemandAttachmentRepository;
import cvt.cv.ppmbackend.repository.DemandCommitteeActionRepository;
import cvt.cv.ppmbackend.repository.DemandHistoryRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionAreaRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import cvt.cv.ppmbackend.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The demand list filters on several types, natures, directions and domains at once. A direction
 * matches as responsible or as a participant (Implementação or Interveniente).
 */
@DataJpaTest
class DemandListFilterJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private DemandRepository demands;

    private DemandService service;

    @BeforeEach
    void setUp() {
        service = new DemandService(demands, mock(DemandPlanningCycleRepository.class), mock(DemandAttachmentRepository.class),
                mock(DemandHistoryRepository.class), mock(DemandParticipatingDirectionRepository.class),
                mock(DemandAreaRepository.class), mock(DemandParticipatingDirectionAreaRepository.class),
                mock(DemandCodeService.class), mock(DemandHistoryService.class), mock(CommitteeService.class),
                mock(CommitteeSuggestionService.class), mock(ProjectService.class), mock(ProgramService.class),
                mock(OperationalPlanService.class), mock(LookupValueService.class), mock(DemandScoringService.class),
                mock(DemandScoreLifecycleService.class), mock(DemandProfileRequirementService.class),
                mock(DemandSuccessCriterionService.class), mock(SupplierRepository.class),
                mock(DemandPlanningCycleService.class), mock(DemandDependencyService.class), mock(PhaseModelRepository.class),
                mock(DemandCommitteeActionRepository.class), mock(DemandCustomPlanningService.class),
                mock(DemandAccessService.class));

        PhaseModel ti = phaseModel("PROJ_TI");
        PhaseModel neg = phaseModel("PROJ_NEG");
        PhaseModel infra = phaseModel("INFRA_TI");
        LookupValue billing = domain("FATURACAO");
        LookupValue network = domain("REDE");

        Demand dem1 = demand("DEM-1", ti, DemandNature.NORMAL, "DSI", "GDS", billing);
        Demand dem2 = demand("DEM-2", neg, DemandNature.REGULATORY, "DFR", "CTB", network);
        Demand dem3 = demand("DEM-3", infra, DemandNature.LEGAL, "DEI", null, null);
        Demand dem4 = demand("DEM-4", ti, DemandNature.CONTRACTUAL, "dsi", "ISI", network);
        dem1.setImportCode("DMK-2027-001");
        dem4.setImportCode("dsi-2027-004");
        /* DCM only takes part; DSI is also a participant of a demand it is responsible for. */
        participant(dem2, "DCM", DirectionParticipationType.INTERVENIENT);
        participant(dem3, "dcm", DirectionParticipationType.IMPLEMANTATION);
        participant(dem1, "DSI", DirectionParticipationType.IMPLEMANTATION);
        em.flush();
    }

    @Test
    void directionAlsoFindsDemandsItTakesPartIn() {
        assertThat(codes(null, null, List.of("dcm"), null)).containsExactlyInAnyOrder("DEM-2", "DEM-3");
    }

    @Test
    void responsibleAndParticipatingDirectionListsTheDemandOnce() {
        assertThat(codes(null, null, List.of("DSI"), null)).containsExactlyInAnyOrder("DEM-1", "DEM-4");
    }

    @Test
    void severalDirectionsMixResponsibleAndParticipating() {
        assertThat(codes(null, null, List.of("DFR", "DCM"), null)).containsExactlyInAnyOrder("DEM-2", "DEM-3");
    }

    @Test
    void noFilterListsAll() {
        assertThat(codes(null, null, null, null)).containsExactlyInAnyOrder("DEM-1", "DEM-2", "DEM-3", "DEM-4");
    }

    @Test
    void severalTypes() {
        assertThat(codes(List.of("proj_ti", "PROJ_NEG"), null, null, null)).containsExactlyInAnyOrder("DEM-1", "DEM-2", "DEM-4");
    }

    @Test
    void severalNatures() {
        assertThat(codes(null, List.of(DemandNature.REGULATORY, DemandNature.LEGAL), null, null))
                .containsExactlyInAnyOrder("DEM-2", "DEM-3");
    }

    @Test
    void severalDirectionsIgnoringCase() {
        assertThat(codes(null, null, List.of("DSI", "dei"), null)).containsExactlyInAnyOrder("DEM-1", "DEM-3", "DEM-4");
    }

    @Test
    void severalDomains() {
        assertThat(codes(null, null, null, List.of("faturacao", "REDE"))).containsExactlyInAnyOrder("DEM-1", "DEM-2", "DEM-4");
    }

    @Test
    void severalAreas() {
        assertThat(codes(null, null, null, List.of("gds", "ISI"), null)).containsExactlyInAnyOrder("DEM-1", "DEM-4");
    }

    @Test
    void areaNarrowsTheDirection() {
        assertThat(codes(null, null, List.of("DSI"), List.of("ISI"), null)).containsExactly("DEM-4");
    }

    @Test
    void importCodesFindTheDemandsOfTheSheet() {
        assertThat(service.list(0, 50, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        List.of("dmk-2027-001", "DSI-2027-004", "NAO-EXISTE"), null, null, null)
                .items()).extracting(DemandResponse::code, DemandResponse::importCode)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("DEM-1", "DMK-2027-001"),
                        org.assertj.core.groups.Tuple.tuple("DEM-4", "dsi-2027-004"));
    }

    @Test
    void filtersCombineWithAnd() {
        assertThat(codes(List.of("PROJ_TI"), null, List.of("DSI"), List.of("REDE"))).containsExactly("DEM-4");
    }

    @Test
    void blankValuesAreIgnored() {
        assertThat(codes(List.of(" "), null, List.of(""), null)).hasSize(4);
    }

    private List<String> codes(List<String> types, List<DemandNature> natures, List<String> directions, List<String> domains) {
        return codes(types, natures, directions, null, domains);
    }

    private List<String> codes(List<String> types, List<DemandNature> natures, List<String> directions, List<String> areas,
            List<String> domains) {
        return service.list(0, 50, null, null, null, types, null, null, natures, null, null, null,
                        null, null, null, null, null, null, null, directions, areas, domains, null, null, null, null)
                .items().stream().map(DemandResponse::code).toList();
    }

    private PhaseModel phaseModel(String code) {
        PhaseModel model = new PhaseModel();
        model.setCode(code);
        model.setName(code);
        return em.persist(model);
    }

    private LookupValue domain(String code) {
        LookupValue value = new LookupValue();
        value.setCategory("DOMAIN");
        value.setCode(code);
        value.setLabel(code);
        return em.persist(value);
    }

    private Demand demand(String code, PhaseModel type, DemandNature nature, String directionCode, String areaCode,
            LookupValue domain) {
        Demand demand = new Demand();
        demand.setCode(code);
        demand.setTitle(code);
        demand.setType(type);
        demand.setNature(nature);
        demand.setDirectionCode(directionCode);
        demand.setAreaCode(areaCode);
        demand.setDomain(domain);
        return em.persist(demand);
    }

    private void participant(Demand demand, String directionCode, DirectionParticipationType type) {
        DemandParticipatingDirection participating = new DemandParticipatingDirection();
        participating.setDemand(demand);
        participating.setDirectionCode(directionCode);
        participating.setParticipationType(type);
        em.persist(participating);
    }
}
