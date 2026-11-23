package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.FinalizeResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.PlanningScenario;
import cvt.cv.ppmbackend.entity.PlanningScenarioItem;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioCriterionRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioItemRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningScenarioFinalizationTest {

    @Mock
    private PlanningScenarioRepository scenarios;
    @Mock
    private PlanningScenarioItemRepository items;
    @Mock
    private PlanningScenarioCriterionRepository criteria;
    @Mock
    private StrategicPlanRepository strategicPlans;
    @Mock
    private OperationalPlanRepository operationalPlans;
    @Mock
    private DemandPlanningCycleRepository planningCycles;
    @Mock
    private ScenarioPoolService pool;
    @Mock
    private ScenarioCriteriaEvaluator evaluator;
    @Mock
    private DemandHistoryService historyService;

    private PlanningScenarioService service;
    private StrategicPlan plan;
    private PlanningScenario scenario;

    @BeforeEach
    void setUp() {
        service = new PlanningScenarioService(scenarios, items, criteria, strategicPlans, operationalPlans,
                planningCycles, pool, evaluator, historyService);
        plan = new StrategicPlan();
        plan.setId(UUID.randomUUID());
        plan.setName("Plano Estratégico 2027-2029");

        scenario = new PlanningScenario();
        scenario.setId(UUID.randomUUID());
        scenario.setStrategicPlan(plan);
        scenario.setName("Cenário Base");
        scenario.setStatus(ScenarioStatus.DRAFT);

        when(scenarios.findById(scenario.getId())).thenReturn(Optional.of(scenario));
        when(scenarios.save(any(PlanningScenario.class))).thenAnswer(i -> i.getArgument(0));
        when(scenarios.findFirstByStrategicPlan_IdAndStatus(plan.getId(), ScenarioStatus.FINAL))
                .thenReturn(Optional.empty());
        when(scenarios.findByStrategicPlan_IdOrderByCreatedAtDesc(plan.getId())).thenReturn(List.of(scenario));
        when(criteria.countByScenario_Id(scenario.getId())).thenReturn(0L);
    }

    @Test
    void includedDemandsGoToPlanWithScenarioRankAndExcludedGoToBacklog() {
        DemandPlanningCycle first = cycle("DEM-001", 3);
        DemandPlanningCycle second = cycle("DEM-002", 1);
        DemandPlanningCycle dropped = cycle("DEM-003", 2);

        PlanningScenarioItem firstItem = item(first, true, 2);
        PlanningScenarioItem secondItem = item(second, true, 1);
        PlanningScenarioItem droppedItem = item(dropped, false, null);

        when(items.findByScenario_Id(scenario.getId()))
                .thenReturn(List.of(firstItem, secondItem, droppedItem));
        when(pool.eligiblePool(plan.getId())).thenReturn(List.of(
                new ScenarioDemandSnapshot(first, 0, 0),
                new ScenarioDemandSnapshot(second, 0, 0),
                new ScenarioDemandSnapshot(dropped, 0, 0)));

        FinalizeResponse response = service.finalizeScenario(scenario.getId(), null, "user-1", "Ana");

        assertThat(response.status()).isEqualTo(ScenarioStatus.FINAL);
        assertThat(response.demandsInPlan()).isEqualTo(2);
        assertThat(response.demandsInBacklog()).isEqualTo(1);

        assertThat(second.getStatus()).isEqualTo(DemandPlanningStatus.IN_PLAN);
        assertThat(second.getPortfolioRank()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(DemandPlanningStatus.IN_PLAN);
        assertThat(first.getPortfolioRank()).isEqualTo(2);

        assertThat(dropped.getStatus()).isEqualTo(DemandPlanningStatus.BACKLOG);
        assertThat(dropped.getPortfolioRank()).isNull();
        assertThat(dropped.getRankBeforeBacklog()).isEqualTo(2);

        assertThat(scenario.getSelectedBy()).isEqualTo("Ana");
        assertThat(scenario.getSelectedAt()).isNotNull();
    }

    @Test
    void aSecondFinalScenarioIsRejected() {
        PlanningScenario existingFinal = new PlanningScenario();
        existingFinal.setId(UUID.randomUUID());
        existingFinal.setName("Cenário Aprovado");
        existingFinal.setStrategicPlan(plan);
        existingFinal.setStatus(ScenarioStatus.FINAL);
        when(scenarios.findFirstByStrategicPlan_IdAndStatus(plan.getId(), ScenarioStatus.FINAL))
                .thenReturn(Optional.of(existingFinal));

        assertThatThrownBy(() -> service.finalizeScenario(scenario.getId(), null, "user-1", "Ana"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Já existe um cenário final");
    }

    @Test
    void demandsUnderReviewCannotBeInTheFinalScenario() {
        DemandPlanningCycle underReview = cycle("DEM-004", 1);
        underReview.setStatus(DemandPlanningStatus.IN_REVIEW);
        when(items.findByScenario_Id(scenario.getId())).thenReturn(List.of(item(underReview, true, 1)));

        assertThatThrownBy(() -> service.finalizeScenario(scenario.getId(), null, "user-1", "Ana"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("em revisão");
    }

    @Test
    void otherDraftScenariosAreArchivedWhenOneIsSelected() {
        DemandPlanningCycle included = cycle("DEM-001", 1);
        PlanningScenario other = new PlanningScenario();
        other.setId(UUID.randomUUID());
        other.setStrategicPlan(plan);
        other.setName("Cenário Alternativo");
        other.setStatus(ScenarioStatus.DRAFT);

        when(items.findByScenario_Id(scenario.getId())).thenReturn(List.of(item(included, true, 1)));
        when(pool.eligiblePool(plan.getId())).thenReturn(List.of(new ScenarioDemandSnapshot(included, 0, 0)));
        when(scenarios.findByStrategicPlan_IdOrderByCreatedAtDesc(plan.getId()))
                .thenReturn(List.of(scenario, other));

        FinalizeResponse response = service.finalizeScenario(scenario.getId(), null, "user-1", "Ana");

        assertThat(response.archivedScenarios()).isEqualTo(1);
        assertThat(other.getStatus()).isEqualTo(ScenarioStatus.ARCHIVED);
    }

    private DemandPlanningCycle cycle(String code, Integer portfolioRank) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode(code);
        demand.setTitle("Demanda " + code);

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setStatus(DemandPlanningStatus.PRIORITIZED);
        cycle.setPortfolioRank(portfolioRank);
        return cycle;
    }

    private PlanningScenarioItem item(DemandPlanningCycle cycle, boolean included, Integer scenarioRank) {
        PlanningScenarioItem item = new PlanningScenarioItem();
        item.setId(UUID.randomUUID());
        item.setScenario(scenario);
        item.setDemandPlanningCycle(cycle);
        item.setIncluded(included);
        item.setScenarioRank(scenarioRank);
        return item;
    }
}
