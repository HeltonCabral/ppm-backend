package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.PlanningScenarioCriterion;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.ScenarioCriterionCode;
import cvt.cv.ppmbackend.enums.ScenarioCriterionOperator;
import cvt.cv.ppmbackend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioCriteriaEvaluatorTest {

    private final ScenarioCriteriaEvaluator evaluator = new ScenarioCriteriaEvaluator();

    @Test
    void numericCriterionFiltersByBlockingDependencies() {
        ScenarioDemandSnapshot lowRisk = snapshot(demand(), 5, 2);
        ScenarioDemandSnapshot highRisk = snapshot(demand(), 5, 4);
        PlanningScenarioCriterion criterion = criterion(ScenarioCriterionCode.BLOCKING_DEPENDENCY_COUNT,
                ScenarioCriterionOperator.LESS_THAN, "3");

        assertThat(evaluator.matches(lowRisk, criterion)).isTrue();
        assertThat(evaluator.matches(highRisk, criterion)).isFalse();
    }

    @Test
    void textCriterionMatchesDirectionCodeOrNameIgnoringCase() {
        Demand demand = demand();
        demand.setDirectionCode("DSI");
        demand.setDirectionName("Direcao de Sistemas");
        ScenarioDemandSnapshot snapshot = snapshot(demand, 0, 0);

        assertThat(evaluator.matches(snapshot,
                criterion(ScenarioCriterionCode.DIRECTION, ScenarioCriterionOperator.IN, "dsi, DRH"))).isTrue();
        assertThat(evaluator.matches(snapshot,
                criterion(ScenarioCriterionCode.DIRECTION, ScenarioCriterionOperator.NOT_IN, "DSI"))).isFalse();
    }

    @Test
    void dateCriterionComparesMandatoryDeadline() {
        Demand demand = demand();
        demand.setMandatoryDeadline(LocalDate.of(2027, 6, 30));
        ScenarioDemandSnapshot snapshot = snapshot(demand, 0, 0);

        assertThat(evaluator.matches(snapshot, criterion(ScenarioCriterionCode.MANDATORY_DEADLINE,
                ScenarioCriterionOperator.BEFORE, "2027-12-31"))).isTrue();
        assertThat(evaluator.matches(snapshot, criterion(ScenarioCriterionCode.MANDATORY_DEADLINE,
                ScenarioCriterionOperator.BETWEEN, "2027-01-01,2027-03-31"))).isFalse();
    }

    @Test
    void allCriteriaMustMatchForTheDemandToEnterTheProposal() {
        Demand demand = demand();
        demand.setNature(DemandNature.REGULATORY);
        demand.setComplexity(DemandComplexity.LOW);
        ScenarioDemandSnapshot snapshot = snapshot(demand, 1, 0);

        List<PlanningScenarioCriterion> criteria = List.of(
                criterion(ScenarioCriterionCode.NATURE, ScenarioCriterionOperator.EQUALS, "REGULATORY"),
                criterion(ScenarioCriterionCode.SCORE, ScenarioCriterionOperator.GREATER_OR_EQUAL, "70"));
        assertThat(evaluator.matches(snapshot, criteria)).isTrue();

        List<PlanningScenarioCriterion> stricter = List.of(
                criterion(ScenarioCriterionCode.NATURE, ScenarioCriterionOperator.EQUALS, "REGULATORY"),
                criterion(ScenarioCriterionCode.SCORE, ScenarioCriterionOperator.GREATER_THAN, "90"));
        assertThat(evaluator.matches(snapshot, stricter)).isFalse();
    }

    @Test
    void demandWithoutValueOnlySatisfiesNegatingOperators() {
        Demand demand = demand();
        DemandPlanningCycle cycle = cycle(demand);
        cycle.setScoreTotal(null);
        ScenarioDemandSnapshot snapshot = new ScenarioDemandSnapshot(cycle, 0, 0);

        assertThat(evaluator.matches(snapshot,
                criterion(ScenarioCriterionCode.SCORE, ScenarioCriterionOperator.GREATER_THAN, "10"))).isFalse();
        assertThat(evaluator.matches(snapshot,
                criterion(ScenarioCriterionCode.SCORE, ScenarioCriterionOperator.NOT_EQUALS, "10"))).isTrue();
    }

    @Test
    void validationRejectsOperatorsAndValuesThatTheCriterionCannotEvaluate() {
        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.DIRECTION,
                ScenarioCriterionOperator.GREATER_THAN, "DSI"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.SCORE,
                ScenarioCriterionOperator.BETWEEN, "10"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.SCORE,
                ScenarioCriterionOperator.EQUALS, "abc"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.MANDATORY_DEADLINE,
                ScenarioCriterionOperator.BEFORE, "30-06-2027"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void portfolioRankAppliesTopNCutAgainstPersistedCyclePosition() {
        Demand demand = demand();
        DemandPlanningCycle cycle = cycle(demand);
        cycle.setPortfolioRank(3);
        ScenarioDemandSnapshot inside = new ScenarioDemandSnapshot(cycle, 0, 0);

        DemandPlanningCycle outsideCycle = cycle(demand());
        outsideCycle.setPortfolioRank(4);
        ScenarioDemandSnapshot outside = new ScenarioDemandSnapshot(outsideCycle, 0, 0);

        DemandPlanningCycle unrankedCycle = cycle(demand());
        unrankedCycle.setPortfolioRank(null);
        ScenarioDemandSnapshot unranked = new ScenarioDemandSnapshot(unrankedCycle, 0, 0);

        PlanningScenarioCriterion criterion = criterion(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "3");

        assertThat(evaluator.matches(inside, criterion)).isTrue();
        assertThat(evaluator.matches(outside, criterion)).isFalse();
        // Sem portfolioRank a demanda fica fora do corte.
        assertThat(evaluator.matches(unranked, criterion)).isFalse();
    }

    @Test
    void portfolioRankReflectsUpdatedCyclePositionAfterReprioritization() {
        Demand demand = demand();
        DemandPlanningCycle cycle = cycle(demand);
        cycle.setPortfolioRank(5);
        ScenarioDemandSnapshot snapshot = new ScenarioDemandSnapshot(cycle, 0, 0);
        PlanningScenarioCriterion criterion = criterion(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "3");

        assertThat(evaluator.matches(snapshot, criterion)).isFalse();

        cycle.setPortfolioRank(2);
        assertThat(evaluator.matches(snapshot, criterion)).isTrue();
    }

    @Test
    void portfolioRankOnlyAcceptsLessOrEqualOperator() {
        for (ScenarioCriterionOperator operator : ScenarioCriterionOperator.values()) {
            if (operator == ScenarioCriterionOperator.LESS_OR_EQUAL) {
                continue;
            }
            assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.PORTFOLIO_RANK, operator, "10"))
                    .as("operador %s deve ser rejeitado", operator)
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void portfolioRankRejectsNonPositiveOrNonIntegerValues() {
        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "0")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "-5")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "2.5")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> evaluator.validate(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "abc")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void portfolioRankAcceptsAnyPositiveIntegerEvenAboveTotalDemands() {
        // Top 500 num plano com poucas demandas continua a ser válido.
        evaluator.validate(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "500");
        evaluator.validate(ScenarioCriterionCode.PORTFOLIO_RANK,
                ScenarioCriterionOperator.LESS_OR_EQUAL, "1");
    }

    private PlanningScenarioCriterion criterion(ScenarioCriterionCode code, ScenarioCriterionOperator operator,
            String value) {
        PlanningScenarioCriterion criterion = new PlanningScenarioCriterion();
        criterion.setCriterionCode(code);
        criterion.setOperator(operator);
        criterion.setValue(value);
        return criterion;
    }

    private Demand demand() {
        Demand demand = new Demand();
        demand.setId(java.util.UUID.randomUUID());
        demand.setCode("DEM-001");
        demand.setTitle("Demanda");
        PhaseModel type = new PhaseModel();
        type.setCode("EVOLUTIVO");
        demand.setType(type);
        return demand;
    }

    private DemandPlanningCycle cycle(Demand demand) {
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(java.util.UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setScoreTotal(new BigDecimal("80.00"));
        return cycle;
    }

    private ScenarioDemandSnapshot snapshot(Demand demand, int total, int blocking) {
        return new ScenarioDemandSnapshot(cycle(demand), total, blocking);
    }
}
