package cvt.cv.ppmbackend.enums;

import java.util.Set;

import static cvt.cv.ppmbackend.enums.ScenarioCriterionOperator.*;

/**
 * Atributos de uma Demanda sobre os quais um cenário pode definir critérios.
 * Cada código declara o tipo de valor que compara e os operadores admissíveis.
 */
public enum ScenarioCriterionCode {
    DIRECTION(ValueKind.TEXT),
    NATURE(ValueKind.TEXT),
    DEMAND_TYPE(ValueKind.TEXT),
    DOMAIN(ValueKind.TEXT),
    STRATEGIC_PILLAR(ValueKind.TEXT),
    STRATEGIC_OBJECTIVE(ValueKind.TEXT),
    SCORE(ValueKind.NUMBER),
    COMPLEXITY(ValueKind.TEXT),
    CAPACITY_STATUS(ValueKind.TEXT),
    MANDATORY_DEADLINE(ValueKind.DATE),
    BLOCKING_DEPENDENCY_COUNT(ValueKind.NUMBER),
    TOTAL_DEPENDENCY_COUNT(ValueKind.NUMBER),
    ESTIMATED_BUDGET(ValueKind.NUMBER),
    // Posição no ranking global de portfólio; usado como corte "Top N" (N = valor do critério).
    PORTFOLIO_RANK(ValueKind.NUMBER, Set.of(LESS_OR_EQUAL));

    public enum ValueKind {
        TEXT, NUMBER, DATE
    }

    private static final Set<ScenarioCriterionOperator> TEXT_OPERATORS = Set.of(EQUALS, NOT_EQUALS, IN, NOT_IN);
    private static final Set<ScenarioCriterionOperator> NUMBER_OPERATORS = Set.of(EQUALS, NOT_EQUALS, IN, NOT_IN,
            GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL, BETWEEN);
    private static final Set<ScenarioCriterionOperator> DATE_OPERATORS = Set.of(EQUALS, NOT_EQUALS, BEFORE, AFTER,
            BETWEEN);

    private final ValueKind valueKind;
    private final Set<ScenarioCriterionOperator> operatorOverride;

    ScenarioCriterionCode(ValueKind valueKind) {
        this(valueKind, null);
    }

    ScenarioCriterionCode(ValueKind valueKind, Set<ScenarioCriterionOperator> operatorOverride) {
        this.valueKind = valueKind;
        this.operatorOverride = operatorOverride;
    }

    public ValueKind valueKind() {
        return valueKind;
    }

    public Set<ScenarioCriterionOperator> supportedOperators() {
        if (operatorOverride != null) {
            return operatorOverride;
        }
        return switch (valueKind) {
            case TEXT -> TEXT_OPERATORS;
            case NUMBER -> NUMBER_OPERATORS;
            case DATE -> DATE_OPERATORS;
        };
    }

    public boolean supports(ScenarioCriterionOperator operator) {
        return operator != null && supportedOperators().contains(operator);
    }
}
