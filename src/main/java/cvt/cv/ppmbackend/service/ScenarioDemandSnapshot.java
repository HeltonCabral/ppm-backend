package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fotografia de uma Demanda elegível, com os atributos sobre os quais os critérios de cenário
 * operam. É construída uma vez por pedido para evitar consultas repetidas por Demanda.
 */
public record ScenarioDemandSnapshot(
        DemandPlanningCycle cycle,
        int totalDependencies,
        int blockingDependencies) {

    public Demand demand() {
        return cycle.getDemand();
    }

    public UUID demandId() {
        return demand().getId();
    }

    public UUID cycleId() {
        return cycle.getId();
    }

    public String directionCode() {
        return demand().getDirectionCode();
    }

    public String directionName() {
        return demand().getDirectionName();
    }

    /** Rótulo usado nas distribuições: prefere o código, cai para o nome. */
    public String directionLabel() {
        String code = directionCode();
        if (code != null && !code.isBlank()) {
            return code.trim();
        }
        String name = directionName();
        return name == null || name.isBlank() ? "SEM DIREÇÃO" : name.trim();
    }

    public DemandNature nature() {
        return demand().getNature();
    }

    public String typeCode() {
        return demand().getType() == null ? null : demand().getType().getCode();
    }

    public String domainCode() {
        return demand().getDomain() == null ? null : demand().getDomain().getCode();
    }

    public String pillarName() {
        return cycle.getStrategicPillar() == null ? null : cycle.getStrategicPillar().getName();
    }

    public UUID pillarId() {
        return cycle.getStrategicPillar() == null ? null : cycle.getStrategicPillar().getId();
    }

    public String objectiveName() {
        return cycle.getStrategicObjective() == null ? null : cycle.getStrategicObjective().getName();
    }

    public UUID objectiveId() {
        return cycle.getStrategicObjective() == null ? null : cycle.getStrategicObjective().getId();
    }

    public BigDecimal score() {
        return cycle.getScoreTotal();
    }

    public DemandComplexity complexity() {
        return demand().getComplexity();
    }

    public String capacityStatus() {
        return cycle.getCapacityStatus();
    }

    public LocalDate mandatoryDeadline() {
        return demand().getMandatoryDeadline();
    }

    public BigDecimal estimatedBudget() {
        return demand().getEstimatedBudget();
    }

    public Integer portfolioRank() {
        return cycle.getPortfolioRank();
    }

    public boolean mandatory() {
        DemandNature nature = nature();
        return nature != null && nature != DemandNature.NORMAL;
    }
}
