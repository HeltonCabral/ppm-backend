package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.PagedDemandsResponse;
import cvt.cv.ppmbackend.dto.DemandDtos.ReprioritizePortfolioRankRequest;
import cvt.cv.ppmbackend.dto.DemandDtos.ReprioritizePortfolioRankResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandAttachmentRepository;
import cvt.cv.ppmbackend.repository.DemandAreaRepository;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandReprioritizationTest {

    @Mock private DemandRepository demands;
    @Mock private DemandPlanningCycleRepository planningCycles;
    @Mock private DemandAttachmentRepository attachments;
    @Mock private DemandHistoryRepository history;
    @Mock private DemandParticipatingDirectionRepository participatingDirections;
    @Mock private DemandAreaRepository demandAreas;
    @Mock private DemandParticipatingDirectionAreaRepository participatingDirectionAreas;
    @Mock private DemandCodeService codeService;
    @Mock private DemandHistoryService historyService;
    @Mock private CommitteeService committees;
    @Mock private CommitteeSuggestionService committeeSuggestions;
    @Mock private ProjectService projects;
    @Mock private ProgramService programs;
    @Mock private OperationalPlanService operationalPlans;
    @Mock private LookupValueService lookups;
    @Mock private DemandScoringService scoringService;
    @Mock private DemandScoreLifecycleService scoreLifecycle;
    @Mock private DemandProfileRequirementService profileRequirements;
    @Mock private DemandSuccessCriterionService successCriteria;
    @Mock private SupplierRepository supplierRepository;
    @Mock private DemandPlanningCycleService planningCycleService;
    @Mock private DemandDependencyService dependencyService;
    @Mock private PhaseModelRepository phaseModels;
    @Mock private DemandCommitteeActionRepository committeeActions;

    private DemandService service;

    private final UUID planId = UUID.randomUUID();
    private StrategicPlan plan;
    private List<DemandPlanningCycle> ranked;

    @BeforeEach
    void setUp() {
        service = new DemandService(demands, planningCycles, attachments, history, participatingDirections,
                demandAreas, participatingDirectionAreas,
                codeService, historyService, committees, committeeSuggestions, projects, programs, operationalPlans,
                lookups, scoringService, scoreLifecycle, profileRequirements, successCriteria, supplierRepository,
                planningCycleService, dependencyService, phaseModels, committeeActions);

        plan = new StrategicPlan();
        plan.setId(planId);
    }

    @Test
    void moveDemFromFirstToThirdReordersContiguously() {
        givenFivePrioritizedDemands();
        DemandPlanningCycle target = ranked.get(0); // DEM-000004 em #1

        stubResolve(target);
        stubRankedFindAll(ranked);

        ReprioritizePortfolioRankResponse response = service.reprioritizePortfolioRank(
                target.getDemand().getId(), planId,
                new ReprioritizePortfolioRankRequest(3, "Realinhamento", "Alinhar com decisão do comité"),
                "carla");

        assertThat(response.previousPortfolioRank()).isEqualTo(1);
        assertThat(response.newPortfolioRank()).isEqualTo(3);
        assertThat(response.reprioritizationReason()).isEqualTo("Realinhamento");
        assertThat(response.reprioritizedBy()).isEqualTo("carla");

        // Ordem final esperada: DEM-000003 #1, DEM-000002 #2, DEM-000004 #3, DEM-000005 #4, DEM-000001 #5
        assertThat(codeAt(1)).isEqualTo("DEM-000003");
        assertThat(codeAt(2)).isEqualTo("DEM-000002");
        assertThat(codeAt(3)).isEqualTo("DEM-000004");
        assertThat(codeAt(4)).isEqualTo("DEM-000005");
        assertThat(codeAt(5)).isEqualTo("DEM-000001");

        verify(planningCycles).saveAll(any(Iterable.class));
    }

    @Test
    void allowsMixedSetOfPrioritizedAndInPlanDemands() {
        givenFivePrioritizedDemands();
        // Simular conjunto misto: as duas primeiras estão IN_PLAN, as restantes PRIORITIZED.
        ranked.get(0).setStatus(DemandPlanningStatus.IN_PLAN);
        ranked.get(1).setStatus(DemandPlanningStatus.IN_PLAN);

        DemandPlanningCycle target = ranked.get(4); // DEM-000001 em #5 (PRIORITIZED)
        stubResolve(target);
        stubRankedFindAll(ranked);

        ReprioritizePortfolioRankResponse response = service.reprioritizePortfolioRank(
                target.getDemand().getId(), planId,
                new ReprioritizePortfolioRankRequest(1, "Topo", "Prioridade máxima"),
                "carla");

        assertThat(response.previousPortfolioRank()).isEqualTo(5);
        assertThat(response.newPortfolioRank()).isEqualTo(1);
        assertThat(codeAt(1)).isEqualTo("DEM-000001");
        assertThat(codeAt(2)).isEqualTo("DEM-000004");
        assertThat(codeAt(5)).isEqualTo("DEM-000005");
    }

    @Test
    void positionZeroFailsWithClearRange() {
        givenFivePrioritizedDemands();
        DemandPlanningCycle target = ranked.get(0);
        stubResolve(target);
        stubRankedFindAll(ranked);

        assertThatThrownBy(() -> service.reprioritizePortfolioRank(
                target.getDemand().getId(), planId,
                new ReprioritizePortfolioRankRequest(0, "x", "y"),
                "carla"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("entre 1 e 5");

        verify(planningCycles, never()).saveAll(any(Iterable.class));
    }

    @Test
    void positionAboveTotalFailsWithClearRange() {
        givenFivePrioritizedDemands();
        DemandPlanningCycle target = ranked.get(0);
        stubResolve(target);
        stubRankedFindAll(ranked);

        assertThatThrownBy(() -> service.reprioritizePortfolioRank(
                target.getDemand().getId(), planId,
                new ReprioritizePortfolioRankRequest(6, "x", "y"),
                "carla"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("entre 1 e 5");
    }

    @Test
    void positionEqualToCurrentRankIsRejected() {
        givenFivePrioritizedDemands();
        DemandPlanningCycle target = ranked.get(2); // portfolioRank=3
        stubResolve(target);
        stubRankedFindAll(ranked);

        assertThatThrownBy(() -> service.reprioritizePortfolioRank(
                target.getDemand().getId(), planId,
                new ReprioritizePortfolioRankRequest(3, "x", "y"),
                "carla"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("#3");
    }

    @Test
    void demandOutsideRankingReturnsConflict() {
        givenFivePrioritizedDemands();
        DemandPlanningCycle backlog = cycle("DEM-000009", DemandPlanningStatus.BACKLOG, null);
        stubResolve(backlog);
        stubRankedFindAll(ranked);

        assertThatThrownBy(() -> service.reprioritizePortfolioRank(
                backlog.getDemand().getId(), planId,
                new ReprioritizePortfolioRankRequest(2, "x", "y"),
                "carla"))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("DEMAND_NOT_IN_PORTFOLIO_RANKING");
                });

        verify(planningCycles, never()).saveAll(any(Iterable.class));
    }

    @Test
    void inReviewExcludesPrioritizedAndInPlanAndForwardsPlanScope() {
        PagedDemandsResponse response = service.listInReview(0, 20, planId);

        assertThat(response.items()).isEmpty();
        ArgumentCaptor<Collection<DemandPlanningStatus>> settled = ArgumentCaptor.forClass(Collection.class);
        verify(committeeActions).findDemandIdsPendingReview(settled.capture(), any());
        assertThat(settled.getValue())
                .containsExactlyInAnyOrder(DemandPlanningStatus.PRIORITIZED, DemandPlanningStatus.IN_PLAN);
    }

    // ----------------------------------------------------------------------- helpers

    /** 5 demandas PRIORITIZED com ranks 1..5, na ordem esperada pelo enunciado. */
    private void givenFivePrioritizedDemands() {
        ranked = List.of(
                cycle("DEM-000004", DemandPlanningStatus.PRIORITIZED, 1),
                cycle("DEM-000003", DemandPlanningStatus.PRIORITIZED, 2),
                cycle("DEM-000002", DemandPlanningStatus.PRIORITIZED, 3),
                cycle("DEM-000005", DemandPlanningStatus.PRIORITIZED, 4),
                cycle("DEM-000001", DemandPlanningStatus.PRIORITIZED, 5));
    }

    private DemandPlanningCycle cycle(String code, DemandPlanningStatus status, Integer rank) {
        Demand d = new Demand();
        d.setId(UUID.randomUUID());
        d.setCode(code);
        d.setTitle("Título " + code);

        DemandPlanningCycle c = new DemandPlanningCycle();
        c.setId(UUID.randomUUID());
        c.setDemand(d);
        c.setStrategicPlan(plan);
        c.setStatus(status);
        c.setPortfolioRank(rank);
        return c;
    }

    private void stubResolve(DemandPlanningCycle target) {
        lenient().when(demands.findByIdAndDeletedAtIsNull(target.getDemand().getId()))
                .thenReturn(Optional.of(target.getDemand()));
        lenient().when(planningCycleService.resolveForMutation(target.getDemand().getId(), planId))
                .thenReturn(target);
    }

    @SuppressWarnings("unchecked")
    private void stubRankedFindAll(List<DemandPlanningCycle> cycles) {
        when(planningCycles.findAll(any(Specification.class))).thenReturn(cycles);
    }

    private String codeAt(int rank) {
        return ranked.stream()
                .filter(c -> c.getPortfolioRank() != null && c.getPortfolioRank() == rank)
                .map(c -> c.getDemand().getCode())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nenhuma demanda com rank " + rank));
    }
}
