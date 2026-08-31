package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCommitteeDecisionDtos.DecisionCommand;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCommitteeDecision;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.CommitteeDecision;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.repository.DemandCommitteeDecisionRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandPlanningCycleBacklogTest {

    @Mock
    private DemandPlanningCycleRepository cycles;
    @Mock
    private DemandCommitteeDecisionRepository decisionRepository;
    @Mock
    private StrategicPlanService strategicPlans;
    @Mock
    private OperationalPlanRepository operationalPlans;
    @Mock
    private StrategicPillarRepository strategicPillars;
    @Mock
    private StrategicElementRepository strategicElements;
    @Mock
    private StrategicObjectiveRepository strategicObjectives;
    @Mock
    private ProgramRepository programs;
    @Mock
    private DemandScoreLifecycleService scoreLifecycle;
    @Mock
    private DemandHistoryService historyService;

    private DemandPlanningCycleService service;
    private StrategicPlan plan;

    @BeforeEach
    void setUp() {
        service = new DemandPlanningCycleService(cycles, decisionRepository, strategicPlans, operationalPlans, strategicPillars,
                strategicElements, strategicObjectives, programs, scoreLifecycle, historyService);
        plan = new StrategicPlan();
        plan.setId(UUID.randomUUID());
        when(decisionRepository.save(any(DemandCommitteeDecision.class))).thenAnswer(invocation -> {
            DemandCommitteeDecision recorded = invocation.getArgument(0);
            recorded.setId(UUID.randomUUID());
            return recorded;
        });
    }

    @Test
    void backlogDecisionStoresPreviousRankAndRenumbersRemainingApprovedDemands() {
        DemandPlanningCycle first = cycle("D-001", DemandPlanningStatus.APPROVED, 1);
        DemandPlanningCycle target = cycle("D-002", DemandPlanningStatus.AWAITING_COMMITTEE, 3);
        DemandPlanningCycle third = cycle("D-003", DemandPlanningStatus.CONDITIONALLY_APPROVED, 5);

        when(cycles.save(target)).thenReturn(target);
        when(cycles.findAll(any(Specification.class))).thenReturn(List.of(third, first));

        service.applyCommitteeDecision(target, new DecisionCommand(CommitteeDecision.BACKLOG,
                "Sem capacidade neste ciclo", null, "ATA-2026-07", "user-1", "Utilizador Teste"));

        ArgumentCaptor<DemandCommitteeDecision> recorded = ArgumentCaptor.forClass(DemandCommitteeDecision.class);
        verify(decisionRepository).save(recorded.capture());
        DemandCommitteeDecision decision = recorded.getValue();

        assertThat(target.getStatus()).isEqualTo(DemandPlanningStatus.BACKLOG);
        assertThat(decision.getDecision()).isEqualTo(CommitteeDecision.BACKLOG);
        assertThat(decision.getPlanningCycle()).isSameAs(target);
        assertThat(decision.getJustification()).isEqualTo("Sem capacidade neste ciclo");
        assertThat(decision.getMeetingReference()).isEqualTo("ATA-2026-07");
        assertThat(decision.getDecisionAt()).isNotNull();
        assertThat(decision.getDecidedBy()).isEqualTo("Utilizador Teste");
        assertThat(target.getCommitteeDecisions()).containsExactly(decision);
        assertThat(target.getRankBeforeBacklog()).isEqualTo(3);
        assertThat(target.getPortfolioRank()).isNull();

        assertThat(first.getPortfolioRank()).isEqualTo(1);
        assertThat(third.getPortfolioRank()).isEqualTo(2);
    }

    @Test
    void backlogDecisionExcludesTargetFromRenumberingEvenWhenStillReturnedByQuery() {
        DemandPlanningCycle first = cycle("D-001", DemandPlanningStatus.APPROVED, 1);
        DemandPlanningCycle target = cycle("D-002", DemandPlanningStatus.AWAITING_COMMITTEE, 2);

        when(cycles.save(target)).thenReturn(target);
        when(cycles.findAll(any(Specification.class))).thenReturn(List.of(first, target));

        service.applyCommitteeDecision(target, new DecisionCommand(CommitteeDecision.BACKLOG,
                "Sem capacidade neste ciclo", null, "ATA-2026-07", "user-1", "Utilizador Teste"));

        ArgumentCaptor<List<DemandPlanningCycle>> captor = ArgumentCaptor.forClass(List.class);
        verify(cycles).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(first);
        assertThat(target.getPortfolioRank()).isNull();
        assertThat(first.getPortfolioRank()).isEqualTo(1);
    }

    @Test
    void backlogDecisionLogsPreviousRankInHistory() {
        DemandPlanningCycle target = cycle("D-002", DemandPlanningStatus.AWAITING_COMMITTEE, 4);

        when(cycles.save(target)).thenReturn(target);
        when(cycles.findAll(any(Specification.class))).thenReturn(List.of());

        service.applyCommitteeDecision(target, new DecisionCommand(CommitteeDecision.BACKLOG,
                "Sem capacidade neste ciclo", null, "ATA-2026-07", "user-1", "Utilizador Teste"));

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(historyService).log(eq(target.getDemand()), eq("COMMITTEE_DECISION_BACKLOG"),
                eq(DemandPlanningStatus.AWAITING_COMMITTEE.name()), eq(DemandPlanningStatus.BACKLOG.name()),
                anyString(), eq("user-1"), eq("Utilizador Teste"), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("decision", "BACKLOG").containsEntry("rankBeforeBacklog", "4");
    }

    private DemandPlanningCycle cycle(String code, DemandPlanningStatus status, Integer portfolioRank) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode(code);
        demand.setTitle("Demanda " + code);

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setStatus(status);
        cycle.setPortfolioRank(portfolioRank);
        return cycle;
    }
}
