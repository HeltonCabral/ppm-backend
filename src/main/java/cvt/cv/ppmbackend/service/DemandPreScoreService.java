package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class DemandPreScoreService {

    public PreScoreResult calculate(Demand demand) {
        int priorityScore = calculatePriorityScore(demand.getInitialPriority());
        int urgencyScore = calculateUrgencyScore(demand.getUrgency());
        int effortScore = calculateEffortScore(demand.getEstimatedEffort());
        int budgetScore = calculateBudgetScore(demand.getEstimatedBudget());
        int dateScore = calculateDateScore(demand.getDesiredDate());

        // Fórmula ponderada
        double weightedScore = (priorityScore * 0.30)
                + (urgencyScore * 0.30)
                + (effortScore * 0.20)
                + (budgetScore * 0.10)
                + (dateScore * 0.10);

        // preScore = (weightedScore / 5) * 100
        BigDecimal preScore = BigDecimal.valueOf((weightedScore / 5.0) * 100)
                .setScale(2, RoundingMode.HALF_UP);

        String classification = classifyPreScore(preScore);

        demand.setPreScore(preScore);
        demand.setPreScoreClassification(classification);

        return new PreScoreResult(preScore, classification);
    }

    private int calculatePriorityScore(String priority) {
        if (priority == null || priority.isBlank()) {
            return 1;
        }
        return switch (normalize(priority)) {
            case "low", "baixa", "baixo" -> 1;
            case "medium", "media", "medio", "média", "médio" -> 3;
            case "high", "alta", "alto" -> 5;
            default -> 1;
        };
    }

    private int calculateUrgencyScore(String urgency) {
        if (urgency == null || urgency.isBlank()) {
            return 1;
        }
        return switch (normalize(urgency)) {
            case "low", "baixa", "baixo" -> 1;
            case "medium", "media", "medio", "média", "médio" -> 3;
            case "high", "alta", "alto" -> 5;
            default -> 1;
        };
    }

    private int calculateEffortScore(String effort) {
        if (effort == null || effort.isBlank()) {
            return 3;
        }
        return switch (normalize(effort)) {
            case "low", "baixa", "baixo" -> 5;
            case "medium", "media", "medio", "média", "médio" -> 3;
            case "high", "alta", "alto" -> 1;
            default -> 3;
        };
    }

    private int calculateBudgetScore(BigDecimal budget) {
        if (budget == null) {
            return 3;
        }

        if (budget.compareTo(BigDecimal.valueOf(500000)) <= 0) {
            return 5;
        } else if (budget.compareTo(BigDecimal.valueOf(2000000)) <= 0) {
            return 4;
        } else if (budget.compareTo(BigDecimal.valueOf(5000000)) <= 0) {
            return 3;
        } else if (budget.compareTo(BigDecimal.valueOf(10000000)) <= 0) {
            return 2;
        } else {
            return 1;
        }
    }

    private int calculateDateScore(LocalDate targetDate) {
        if (targetDate == null) {
            return 1;
        }

        long daysUntilTarget = ChronoUnit.DAYS.between(LocalDate.now(), targetDate);

        if (daysUntilTarget <= 30) {
            return 5;
        } else if (daysUntilTarget <= 90) {
            return 4;
        } else if (daysUntilTarget <= 180) {
            return 3;
        } else {
            return 2;
        }
    }

    private String classifyPreScore(BigDecimal preScore) {
        double score = preScore.doubleValue();

        if (score >= 80) {
            return "VERY_HIGH";
        } else if (score >= 65) {
            return "HIGH";
        } else if (score >= 50) {
            return "MEDIUM";
        } else if (score >= 35) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }

    private String normalize(String value) {
        return value.toLowerCase().trim()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ã", "a")
                .replace("õ", "o")
                .replace("â", "a")
                .replace("ê", "e")
                .replace("ô", "o");
    }

    public record PreScoreResult(BigDecimal preScore, String preScoreClassification) {
    }
}
