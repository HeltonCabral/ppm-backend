package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.DemandResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandArea;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirectionArea;
import cvt.cv.ppmbackend.enums.AreaRole;
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
 * The demand list filters by participating area ("DIR/AREA"): an intervenient area of the
 * responsible direction, or an area a participating direction brings. The responsible area
 * itself is not a participating one.
 */
@DataJpaTest
class DemandParticipatingAreaFilterJpaTest {

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

        /* A: DSI/GDS responsible, ISI intervenes. */
        Demand a = demand("DEM-A", "DSI", "GDS");
        demandArea(a, "GDS", AreaRole.RESPONSIBLE);
        demandArea(a, "ISI", AreaRole.INTERVENIENT);
        /* B: DCM takes part with its area MKT. */
        Demand b = demand("DEM-B", "DFR", "CTB");
        participant(b, "DCM", "MKT");
        /* C: DSI takes part, listing GDS among its areas. */
        Demand c = demand("DEM-C", "DEI", null);
        DemandParticipatingDirection dsi = participant(c, "DSI", null);
        participantArea(dsi, "GDS", AreaRole.INTERVENIENT);
        /* D: DSI/GDS responsible and also listed as participant with its own area. */
        Demand d = demand("DEM-D", "DSI", "GDS");
        participant(d, "DSI", "GDS");
        em.flush();
    }

    @Test
    void intervenientAreaOfTheResponsibleDirection() {
        assertThat(codes("DSI/ISI")).containsExactly("DEM-A");
    }

    @Test
    void areaOfAParticipatingDirection() {
        assertThat(codes("DCM/MKT")).containsExactly("DEM-B");
    }

    @Test
    void areaListedUnderAParticipatingDirection() {
        assertThat(codes("DSI/GDS")).containsExactly("DEM-C");
    }

    @Test
    void areaWithoutDirectionMatchesAnyDirection() {
        assertThat(codes("GDS")).containsExactly("DEM-C");
    }

    @Test
    void directionMustMatch() {
        assertThat(codes("DFR/MKT")).isEmpty();
    }

    @Test
    void severalAreasIgnoringCase() {
        assertThat(codes("dsi/isi", "DCM/MKT")).containsExactlyInAnyOrder("DEM-A", "DEM-B");
    }

    @Test
    void combinesWithTheDirectionFilter() {
        assertThat(service.list(0, 50, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, List.of("DFR"), null, null,
                        List.of("DCM/MKT", "DSI/ISI"), null, null, null, null)
                .items()).extracting(DemandResponse::code).containsExactly("DEM-B");
    }

    @Test
    void responsibleDirectionIgnoresParticipants() {
        assertThat(directionCodes(null, List.of("dsi"), null)).containsExactlyInAnyOrder("DEM-A", "DEM-D");
    }

    @Test
    void participatingDirectionIgnoresTheResponsibleOne() {
        assertThat(directionCodes(null, null, List.of("DSI"))).containsExactlyInAnyOrder("DEM-C", "DEM-D");
        assertThat(directionCodes(null, null, List.of("DCM", "dei"))).containsExactly("DEM-B");
    }

    @Test
    void responsibleAndParticipatingDirectionsCombineWithAnd() {
        assertThat(directionCodes(null, List.of("DSI"), List.of("DSI"))).containsExactly("DEM-D");
    }

    @Test
    void directionCodeStillMatchesResponsibleOrParticipating() {
        assertThat(directionCodes(List.of("DSI"), null, null)).containsExactlyInAnyOrder("DEM-A", "DEM-C", "DEM-D");
    }

    private List<String> directionCodes(List<String> any, List<String> responsible, List<String> participating) {
        return service.list(0, 50, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, any, responsible, participating, null, null,
                        null, null, null, null, null)
                .items().stream().map(DemandResponse::code).toList();
    }

    private List<String> codes(String... areas) {
        return service.list(0, 50, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, List.of(areas), null, null, null, null)
                .items().stream().map(DemandResponse::code).toList();
    }

    private Demand demand(String code, String directionCode, String areaCode) {
        Demand demand = new Demand();
        demand.setCode(code);
        demand.setTitle(code);
        demand.setDirectionCode(directionCode);
        demand.setAreaCode(areaCode);
        return em.persist(demand);
    }

    private void demandArea(Demand demand, String areaCode, AreaRole role) {
        DemandArea area = new DemandArea();
        area.setDemand(demand);
        area.setAreaCode(areaCode);
        area.setRole(role);
        em.persist(area);
    }

    private DemandParticipatingDirection participant(Demand demand, String directionCode, String areaCode) {
        DemandParticipatingDirection participating = new DemandParticipatingDirection();
        participating.setDemand(demand);
        participating.setDirectionCode(directionCode);
        participating.setAreaCode(areaCode);
        participating.setParticipationType(DirectionParticipationType.INTERVENIENT);
        return em.persist(participating);
    }

    private void participantArea(DemandParticipatingDirection direction, String areaCode, AreaRole role) {
        DemandParticipatingDirectionArea area = new DemandParticipatingDirectionArea();
        area.setParticipatingDirection(direction);
        area.setAreaCode(areaCode);
        area.setRole(role);
        em.persist(area);
    }
}
