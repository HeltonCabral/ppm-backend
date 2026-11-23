package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.PlanningScenarioCriterion;
import cvt.cv.ppmbackend.enums.ScenarioCriterionCode;
import cvt.cv.ppmbackend.enums.ScenarioCriterionOperator;
import cvt.cv.ppmbackend.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Avalia os critérios dinâmicos de um cenário contra uma Demanda elegível.
 * Só produz uma proposta de composição — nunca toca no score oficial da Demanda.
 */
@Service
public class ScenarioCriteriaEvaluator {

    /** Uma Demanda entra na proposta quando satisfaz TODOS os critérios do cenário. */
    public boolean matches(ScenarioDemandSnapshot snapshot, List<PlanningScenarioCriterion> criteria) {
        for (PlanningScenarioCriterion criterion : criteria) {
            if (!matches(snapshot, criterion)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(ScenarioDemandSnapshot snapshot, PlanningScenarioCriterion criterion) {
        return switch (criterion.getCriterionCode().valueKind()) {
            case TEXT -> matchesText(textValuesOf(snapshot, criterion.getCriterionCode()), criterion);
            case NUMBER -> matchesNumber(numberValueOf(snapshot, criterion.getCriterionCode()), criterion);
            case DATE -> matchesDate(dateValueOf(snapshot, criterion.getCriterionCode()), criterion);
        };
    }

    /** Valida o critério antes de o persistir, para nunca guardar algo que não é avaliável. */
    public void validate(ScenarioCriterionCode code, ScenarioCriterionOperator operator, String value) {
        if (!code.supports(operator)) {
            throw new BadRequestException("O operador " + operator + " não é aplicável ao critério " + code
                    + ". Operadores válidos: " + code.supportedOperators());
        }
        List<String> parts = split(value);
        if (parts.isEmpty()) {
            throw new BadRequestException("value é obrigatório para o critério " + code);
        }
        if (operator == ScenarioCriterionOperator.BETWEEN && parts.size() != 2) {
            throw new BadRequestException("BETWEEN exige exatamente dois valores separados por vírgula");
        }
        if (operator != ScenarioCriterionOperator.IN && operator != ScenarioCriterionOperator.NOT_IN
                && operator != ScenarioCriterionOperator.BETWEEN && parts.size() > 1) {
            throw new BadRequestException("O operador " + operator + " aceita apenas um valor");
        }
        if (code == ScenarioCriterionCode.PORTFOLIO_RANK) {
            parts.forEach(ScenarioCriteriaEvaluator::parsePositiveRankValue);
            return;
        }
        switch (code.valueKind()) {
            case NUMBER -> parts.forEach(part -> parseNumber(part, code));
            case DATE -> parts.forEach(part -> parseDate(part, code));
            case TEXT -> {
                // qualquer texto é aceite
            }
        }
    }

    // ------------------------------------------------------------------ texto

    private List<String> textValuesOf(ScenarioDemandSnapshot snapshot, ScenarioCriterionCode code) {
        List<String> values = new ArrayList<>();
        switch (code) {
            case DIRECTION -> {
                values.add(snapshot.directionCode());
                values.add(snapshot.directionName());
            }
            case NATURE -> values.add(snapshot.nature() == null ? null : snapshot.nature().name());
            case DEMAND_TYPE -> values.add(snapshot.typeCode());
            case DOMAIN -> values.add(snapshot.domainCode());
            case STRATEGIC_PILLAR -> {
                values.add(snapshot.pillarName());
                values.add(snapshot.pillarId() == null ? null : snapshot.pillarId().toString());
            }
            case STRATEGIC_OBJECTIVE -> {
                values.add(snapshot.objectiveName());
                values.add(snapshot.objectiveId() == null ? null : snapshot.objectiveId().toString());
            }
            case COMPLEXITY -> values.add(snapshot.complexity() == null ? null : snapshot.complexity().name());
            case CAPACITY_STATUS -> values.add(snapshot.capacityStatus());
            default -> throw new IllegalStateException("Critério de texto não suportado: " + code);
        }
        values.removeIf(value -> value == null || value.isBlank());
        return values.stream().map(ScenarioCriteriaEvaluator::norm).toList();
    }

    private boolean matchesText(List<String> demandValues, PlanningScenarioCriterion criterion) {
        List<String> expected = split(criterion.getValue()).stream().map(ScenarioCriteriaEvaluator::norm).toList();
        boolean contains = demandValues.stream().anyMatch(expected::contains);
        return switch (criterion.getOperator()) {
            case EQUALS, IN -> contains;
            case NOT_EQUALS, NOT_IN -> !contains;
            default -> false;
        };
    }

    // ----------------------------------------------------------------- número

    private BigDecimal numberValueOf(ScenarioDemandSnapshot snapshot, ScenarioCriterionCode code) {
        return switch (code) {
            case SCORE -> snapshot.score();
            case BLOCKING_DEPENDENCY_COUNT -> BigDecimal.valueOf(snapshot.blockingDependencies());
            case TOTAL_DEPENDENCY_COUNT -> BigDecimal.valueOf(snapshot.totalDependencies());
            case ESTIMATED_BUDGET -> snapshot.estimatedBudget();
            case PORTFOLIO_RANK -> snapshot.portfolioRank() == null ? null
                    : BigDecimal.valueOf(snapshot.portfolioRank());
            default -> throw new IllegalStateException("Critério numérico não suportado: " + code);
        };
    }

    private boolean matchesNumber(BigDecimal actual, PlanningScenarioCriterion criterion) {
        if (actual == null) {
            // Sem valor, a Demanda só satisfaz critérios de negação.
            return criterion.getOperator() == ScenarioCriterionOperator.NOT_EQUALS
                    || criterion.getOperator() == ScenarioCriterionOperator.NOT_IN;
        }
        List<String> parts = split(criterion.getValue());
        ScenarioCriterionCode code = criterion.getCriterionCode();
        return switch (criterion.getOperator()) {
            case EQUALS -> actual.compareTo(parseNumber(parts.get(0), code)) == 0;
            case NOT_EQUALS -> actual.compareTo(parseNumber(parts.get(0), code)) != 0;
            case IN -> parts.stream().anyMatch(part -> actual.compareTo(parseNumber(part, code)) == 0);
            case NOT_IN -> parts.stream().noneMatch(part -> actual.compareTo(parseNumber(part, code)) == 0);
            case GREATER_THAN -> actual.compareTo(parseNumber(parts.get(0), code)) > 0;
            case GREATER_OR_EQUAL -> actual.compareTo(parseNumber(parts.get(0), code)) >= 0;
            case LESS_THAN -> actual.compareTo(parseNumber(parts.get(0), code)) < 0;
            case LESS_OR_EQUAL -> actual.compareTo(parseNumber(parts.get(0), code)) <= 0;
            case BETWEEN -> actual.compareTo(parseNumber(parts.get(0), code)) >= 0
                    && actual.compareTo(parseNumber(parts.get(1), code)) <= 0;
            default -> false;
        };
    }

    // ------------------------------------------------------------------- data

    private LocalDate dateValueOf(ScenarioDemandSnapshot snapshot, ScenarioCriterionCode code) {
        if (code == ScenarioCriterionCode.MANDATORY_DEADLINE) {
            return snapshot.mandatoryDeadline();
        }
        throw new IllegalStateException("Critério de data não suportado: " + code);
    }

    private boolean matchesDate(LocalDate actual, PlanningScenarioCriterion criterion) {
        if (actual == null) {
            return criterion.getOperator() == ScenarioCriterionOperator.NOT_EQUALS;
        }
        List<String> parts = split(criterion.getValue());
        ScenarioCriterionCode code = criterion.getCriterionCode();
        return switch (criterion.getOperator()) {
            case EQUALS -> actual.isEqual(parseDate(parts.get(0), code));
            case NOT_EQUALS -> !actual.isEqual(parseDate(parts.get(0), code));
            case BEFORE -> actual.isBefore(parseDate(parts.get(0), code));
            case AFTER -> actual.isAfter(parseDate(parts.get(0), code));
            case BETWEEN -> !actual.isBefore(parseDate(parts.get(0), code))
                    && !actual.isAfter(parseDate(parts.get(1), code));
            default -> false;
        };
    }

    // ------------------------------------------------------------------ utils

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static String norm(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal parseNumber(String value, ScenarioCriterionCode code) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Valor numérico inválido para o critério " + code + ": " + value);
        }
    }

    private static int parsePositiveRankValue(String value) {
        String trimmed = value == null ? "" : value.trim();
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < 1) {
                throw new BadRequestException(
                        "value do critério PORTFOLIO_RANK deve ser um inteiro >= 1: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BadRequestException(
                    "value do critério PORTFOLIO_RANK deve ser um inteiro >= 1: " + value);
        }
    }

    private LocalDate parseDate(String value, ScenarioCriterionCode code) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(
                    "Data inválida para o critério " + code + ": " + value + " (formato esperado yyyy-MM-dd)");
        }
    }
}
