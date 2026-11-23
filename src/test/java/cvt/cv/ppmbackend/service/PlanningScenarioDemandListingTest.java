package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.AnalysisResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioDemandPage;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioDemandRow;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.PlanningScenario;
import cvt.cv.ppmbackend.entity.PlanningScenarioItem;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.DemandScenarioSituation;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioItemRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import cvt.cv.ppmbackend.service.PlanningScenarioAnalysisService.ScenarioDemandFilters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningScenarioDemandListingTest {

    @Mock
    private PlanningScenarioService scenarioService;
    @Mock
    private PlanningScenarioItemRepository items;
    @Mock
    private ScenarioPoolService pool;
    @Mock
    private DemandProfileRequirementRepository profileRequirements;
    @Mock
    private DemandResourceDerivationService resourceDerivation;
    @Mock
    private DemandDependencyRepository dependencies;
    @Mock
    private DemandCapacityAnalysisService capacityAnalysis;
    private final CapacityCalculator capacityCalculator =
            new CapacityCalculator(new cvt.cv.ppmbackend.config.CapacityThresholdsProperties());
    @Mock
    private StrategicPlanRepository strategicPlans;

    private PlanningScenarioAnalysisService service;
    private StrategicPlan plan;
    private PlanningScenario scenario;

    @BeforeEach
    void setUp() {
        service = new PlanningScenarioAnalysisService(scenarioService, items, pool,
                new DemandRequirementsResolver(resourceDerivation, profileRequirements), dependencies,
                capacityAnalysis, capacityCalculator, strategicPlans);
        plan = new StrategicPlan();
        plan.setId(UUID.randomUUID());
        scenario = new PlanningScenario();
        scenario.setId(UUID.randomUUID());
        scenario.setStrategicPlan(plan);
        scenario.setName("Cenário Base");
        scenario.setStatus(ScenarioStatus.DRAFT);
        when(scenarioService.requireScenario(scenario.getId())).thenReturn(scenario);
        when(capacityAnalysis.statusesForStrategicPlan(plan.getId())).thenReturn(java.util.Map.of());
    }

    @Test
    void defaultOrderIsIncludedThenUnderReviewThenExcluded() {
        DemandPlanningCycle excluded = cycle("DEM-EXC", DemandPlanningStatus.PRIORITIZED, 1);
        DemandPlanningCycle underReview = cycle("DEM-REV", DemandPlanningStatus.IN_REVIEW, 2);
        DemandPlanningCycle includedSecond = cycle("DEM-IN2", DemandPlanningStatus.PRIORITIZED, 8);
        DemandPlanningCycle includedFirst = cycle("DEM-IN1", DemandPlanningStatus.PRIORITIZED, 9);

        when(pool.portfolioPool(plan.getId())).thenReturn(List.of(
                new ScenarioDemandSnapshot(excluded, 0, 0),
                new ScenarioDemandSnapshot(underReview, 0, 0),
                new ScenarioDemandSnapshot(includedSecond, 0, 0),
                new ScenarioDemandSnapshot(includedFirst, 0, 0)));
        when(items.findByScenario_Id(scenario.getId())).thenReturn(List.of(
                item(includedFirst, true, 1),
                item(includedSecond, true, 2)));

        ScenarioDemandPage page = service.demands(scenario.getId(), emptyFilters(), 0, 20);

        assertThat(page.items()).extracting(ScenarioDemandRow::code)
                .containsExactly("DEM-IN1", "DEM-IN2", "DEM-REV", "DEM-EXC");
        assertThat(page.items()).extracting(ScenarioDemandRow::situation)
                .containsExactly(DemandScenarioSituation.INCLUDED, DemandScenarioSituation.INCLUDED,
                        DemandScenarioSituation.IN_REVIEW, DemandScenarioSituation.EXCLUDED);
        assertThat(page.counters().included()).isEqualTo(2);
        assertThat(page.counters().inReview()).isEqualTo(1);
        assertThat(page.counters().excluded()).isEqualTo(1);
    }

    @Test
    void demandUnderReviewIsNeverReportedAsIncludedEvenIfPresentInTheScenario() {
        DemandPlanningCycle underReview = cycle("DEM-REV", DemandPlanningStatus.IN_REVIEW, 1);
        when(pool.portfolioPool(plan.getId()))
                .thenReturn(List.of(new ScenarioDemandSnapshot(underReview, 0, 0)));
        when(items.findByScenario_Id(scenario.getId())).thenReturn(List.of(item(underReview, true, 1)));

        ScenarioDemandPage page = service.demands(scenario.getId(), emptyFilters(), 0, 20);

        assertThat(page.items()).singleElement()
                .extracting(ScenarioDemandRow::situation)
                .isEqualTo(DemandScenarioSituation.IN_REVIEW);
    }

    @Test
    void filtersNarrowThePoolAndPagingSlicesTheResult() {
        DemandPlanningCycle dsi = cycle("DEM-001", DemandPlanningStatus.PRIORITIZED, 1);
        dsi.getDemand().setDirectionCode("DSI");
        DemandPlanningCycle drh = cycle("DEM-002", DemandPlanningStatus.PRIORITIZED, 2);
        drh.getDemand().setDirectionCode("DRH");

        when(pool.portfolioPool(plan.getId())).thenReturn(List.of(
                new ScenarioDemandSnapshot(dsi, 0, 0), new ScenarioDemandSnapshot(drh, 0, 0)));
        when(items.findByScenario_Id(scenario.getId())).thenReturn(List.of());

        ScenarioDemandFilters byDirection = new ScenarioDemandFilters(null, "dsi", null, null, null, null, null,
                null, null, null, null, null, null);
        ScenarioDemandPage filtered = service.demands(scenario.getId(), byDirection, 0, 20);
        assertThat(filtered.items()).extracting(ScenarioDemandRow::code).containsExactly("DEM-001");

        ScenarioDemandPage firstPage = service.demands(scenario.getId(), emptyFilters(), 0, 1);
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.totalItems()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    // ---- plan-level analysis + demands ------------------------------------

    @Test
    void planDemandsAndAnalysisAgreeOnCountersAndCostForMixedPool() {
        when(strategicPlans.findById(plan.getId())).thenReturn(Optional.of(plan));

        DemandPlanningCycle mandatory = mandatoryCycle("DEM-OBR", 1, new BigDecimal("100.00"));
        DemandPlanningCycle prioritized = cycle("DEM-PRI", DemandPlanningStatus.PRIORITIZED, 2);
        prioritized.getDemand().setEstimatedBudget(new BigDecimal("200.50"));
        DemandPlanningCycle inReview = cycle("DEM-REV", DemandPlanningStatus.IN_REVIEW, 3);
        inReview.getDemand().setEstimatedBudget(new BigDecimal("999.99"));
        DemandPlanningCycle inPlan = cycle("DEM-IPL", DemandPlanningStatus.IN_PLAN, 4);
        DemandPlanningCycle backlog = cycle("DEM-BKL", DemandPlanningStatus.BACKLOG, 5);

        when(pool.portfolioPool(plan.getId())).thenReturn(List.of(
                new ScenarioDemandSnapshot(mandatory, 0, 0),
                new ScenarioDemandSnapshot(prioritized, 0, 0),
                new ScenarioDemandSnapshot(inReview, 0, 0),
                new ScenarioDemandSnapshot(inPlan, 0, 0),
                new ScenarioDemandSnapshot(backlog, 0, 0)));

        ScenarioDemandPage page = service.planDemands(plan.getId(), emptyFilters(), 0, 20);
        AnalysisResponse analysisResponse = service.planAnalysis(plan.getId());

        assertThat(page.items()).extracting(ScenarioDemandRow::code)
                .containsExactly("DEM-OBR", "DEM-PRI", "DEM-REV");
        assertThat(page.items()).extracting(ScenarioDemandRow::situation).containsExactly(
                DemandScenarioSituation.INCLUDED, DemandScenarioSituation.INCLUDED,
                DemandScenarioSituation.IN_REVIEW);
        assertThat(page.items()).extracting(ScenarioDemandRow::scenarioRank).containsOnlyNulls();
        assertThat(page.items()).extracting(ScenarioDemandRow::inclusionSource).containsOnlyNulls();
        assertThat(page.items()).extracting(ScenarioDemandRow::comment).containsOnlyNulls();

        assertThat(page.counters().excluded()).isZero();
        assertThat(page.counters().included()).isEqualTo(analysisResponse.totals().included());
        assertThat(page.counters().inReview()).isEqualTo(analysisResponse.totals().inReview());
        assertThat(page.totalItems()).isEqualTo(analysisResponse.totals().eligibleDemands());

        BigDecimal includedBudgetSum = page.items().stream()
                .filter(row -> row.situation() == DemandScenarioSituation.INCLUDED)
                .map(ScenarioDemandRow::estimatedBudget)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(includedBudgetSum).isEqualByComparingTo(analysisResponse.metrics().estimatedCost());
        assertThat(analysisResponse.metrics().mandatoryIncluded()).isEqualTo(1);
    }

    @Test
    void planDemandsProducesSameFieldsAsScenarioPoolExceptScenarioSpecificOnes() {
        when(strategicPlans.findById(plan.getId())).thenReturn(Optional.of(plan));

        DemandPlanningCycle prioritized = cycle("DEM-PRI", DemandPlanningStatus.PRIORITIZED, 7);
        prioritized.getDemand().setEstimatedBudget(new BigDecimal("321.00"));
        prioritized.getDemand().setDirectionCode("DSI");
        prioritized.getDemand().setRequester("Ana");
        List<ScenarioDemandSnapshot> snapshots = List.of(new ScenarioDemandSnapshot(prioritized, 3, 1));

        when(pool.portfolioPool(plan.getId())).thenReturn(snapshots);
        when(items.findByScenario_Id(scenario.getId())).thenReturn(List.of(item(prioritized, true, 1)));

        ScenarioDemandRow scenarioRow = service.demands(scenario.getId(), emptyFilters(), 0, 20).items().get(0);
        ScenarioDemandRow planRow = service.planDemands(plan.getId(), emptyFilters(), 0, 20).items().get(0);

        assertThat(planRow.demandId()).isEqualTo(scenarioRow.demandId());
        assertThat(planRow.planningCycleId()).isEqualTo(scenarioRow.planningCycleId());
        assertThat(planRow.code()).isEqualTo(scenarioRow.code());
        assertThat(planRow.title()).isEqualTo(scenarioRow.title());
        assertThat(planRow.requester()).isEqualTo(scenarioRow.requester());
        assertThat(planRow.directionCode()).isEqualTo(scenarioRow.directionCode());
        assertThat(planRow.directionName()).isEqualTo(scenarioRow.directionName());
        assertThat(planRow.nature()).isEqualTo(scenarioRow.nature());
        assertThat(planRow.type()).isEqualTo(scenarioRow.type());
        assertThat(planRow.domain()).isEqualTo(scenarioRow.domain());
        assertThat(planRow.strategicPillar()).isEqualTo(scenarioRow.strategicPillar());
        assertThat(planRow.strategicObjective()).isEqualTo(scenarioRow.strategicObjective());
        assertThat(planRow.score()).isEqualTo(scenarioRow.score());
        assertThat(planRow.scoreStatus()).isEqualTo(scenarioRow.scoreStatus());
        assertThat(planRow.portfolioRank()).isEqualTo(scenarioRow.portfolioRank());
        assertThat(planRow.complexity()).isEqualTo(scenarioRow.complexity());
        assertThat(planRow.complexityScore()).isEqualTo(scenarioRow.complexityScore());
        assertThat(planRow.capacityStatus()).isEqualTo(scenarioRow.capacityStatus());
        assertThat(planRow.estimatedBudget()).isEqualTo(scenarioRow.estimatedBudget());
        assertThat(planRow.mandatoryDeadline()).isEqualTo(scenarioRow.mandatoryDeadline());
        assertThat(planRow.totalDependencies()).isEqualTo(scenarioRow.totalDependencies());
        assertThat(planRow.blockingDependencies()).isEqualTo(scenarioRow.blockingDependencies());

        assertThat(planRow.situation()).isEqualTo(DemandScenarioSituation.INCLUDED);
        assertThat(planRow.scenarioRank()).isNull();
        assertThat(planRow.inclusionSource()).isNull();
        assertThat(planRow.comment()).isNull();
    }

    @Test
    void planAnalysisAndDemandsDoNotWriteAnyScenarioState() {
        when(strategicPlans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(pool.portfolioPool(plan.getId())).thenReturn(List.of(
                new ScenarioDemandSnapshot(cycle("DEM-A", DemandPlanningStatus.PRIORITIZED, 1), 0, 0)));

        service.planAnalysis(plan.getId());
        service.planDemands(plan.getId(), emptyFilters(), 0, 20);

        verify(items, never()).save(org.mockito.ArgumentMatchers.any());
        verify(items, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(items, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void planAnalysisRejectsUnknownStrategicPlan() {
        UUID missing = UUID.randomUUID();
        when(strategicPlans.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.planAnalysis(missing))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void planDemandsRejectsUnknownStrategicPlan() {
        UUID missing = UUID.randomUUID();
        when(strategicPlans.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.planDemands(missing, emptyFilters(), 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void planDemandsPaginationCoversAllRowsExactlyOnce() {
        when(strategicPlans.findById(plan.getId())).thenReturn(Optional.of(plan));

        List<ScenarioDemandSnapshot> snapshots = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            snapshots.add(new ScenarioDemandSnapshot(
                    cycle(String.format("DEM-%03d", i), DemandPlanningStatus.PRIORITIZED, i), 0, 0));
        }
        when(pool.portfolioPool(plan.getId())).thenReturn(snapshots);

        java.util.Set<String> seen = new java.util.HashSet<>();
        int totalPages = -1;
        for (int page = 0; page < 3; page++) {
            ScenarioDemandPage slice = service.planDemands(plan.getId(), emptyFilters(), page, 2);
            slice.items().forEach(row -> assertThat(seen.add(row.code())).isTrue());
            totalPages = slice.totalPages();
        }
        assertThat(seen).hasSize(5);
        assertThat(totalPages).isEqualTo(3);
    }

    private ScenarioDemandFilters emptyFilters() {
        return new ScenarioDemandFilters(null, null, null, null, null, null, null, null, null, null, null, null,
                null);
    }

    private DemandPlanningCycle cycle(String code, DemandPlanningStatus status, Integer portfolioRank) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode(code);
        demand.setTitle("Demanda " + code);
        demand.setNature(DemandNature.NORMAL);

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setStatus(status);
        cycle.setPortfolioRank(portfolioRank);
        return cycle;
    }

    private DemandPlanningCycle mandatoryCycle(String code, Integer portfolioRank, BigDecimal estimatedBudget) {
        DemandPlanningCycle cycle = cycle(code, DemandPlanningStatus.PRIORITIZED, portfolioRank);
        cycle.getDemand().setNature(DemandNature.CORPORATE_MANDATORY);
        cycle.getDemand().setEstimatedBudget(estimatedBudget);
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
