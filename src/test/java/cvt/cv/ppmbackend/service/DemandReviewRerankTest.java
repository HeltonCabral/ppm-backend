package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.repository.DemandCommitteeActionRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemandReviewRerankTest {

    @Mock private DemandPlanningCycleRepository cycles;
    @Mock private DemandCommitteeActionRepository actions;
    @Mock private StrategicPlanService strategicPlans;
    @Mock private OperationalPlanRepository operationalPlans;
    @Mock private StrategicPillarRepository strategicPillars;
    @Mock private StrategicElementRepository strategicElements;
    @Mock private StrategicObjectiveRepository strategicObjectives;
    @Mock private ProgramRepository programs;
    @Mock private DemandScoreLifecycleService scoreLifecycle;
    @Mock private DemandScoringService scoringService;
    @Mock private DemandHistoryService historyService;

    private DemandPlanningCycleService service;
    private final UUID planId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DemandPlanningCycleService(cycles, actions, strategicPlans, operationalPlans, strategicPillars,
                strategicElements, strategicObjectives, programs, scoreLifecycle, scoringService, historyService);
        when(cycles.save(any(DemandPlanningCycle.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void movingToReviewInvalidatesScoreAndReranksRemainingPortfolio() {
        DemandPlanningCycle cycle = cycle(DemandPlanningStatus.PRIORITIZED, new BigDecimal("87.50"), 3);
        // Simula a invalidação feita pelo lifecycle ao entrar em revisão.
        doAnswer(inv -> {
            cycle.setScoreTotal(null);
            cycle.setPortfolioRank(null);
            return null;
        }).when(scoreLifecycle).applyStatusTransition(eq(cycle), eq(DemandPlanningStatus.PRIORITIZED),
                eq(DemandPlanningStatus.IN_REVIEW), anyString());

        service.transitionStatus(cycle, DemandPlanningStatus.IN_REVIEW, "Pedido de revisão", "ana");

        verify(scoringService).recomputeRanks(planId);
    }

    @Test
    void transitionWithoutScoreDoesNotRecomputeRanks() {
        DemandPlanningCycle cycle = cycle(DemandPlanningStatus.IN_ANALYSIS, null, null);

        service.transitionStatus(cycle, DemandPlanningStatus.IN_PRIORITIZATION, null, "ana");

        verify(scoringService, never()).recomputeRanks(any());
    }

    private DemandPlanningCycle cycle(DemandPlanningStatus status, BigDecimal scoreTotal, Integer rank) {
        StrategicPlan plan = new StrategicPlan();
        plan.setId(planId);
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setStatus(status);
        cycle.setScoreTotal(scoreTotal);
        cycle.setPortfolioRank(rank);
        return cycle;
    }
}
