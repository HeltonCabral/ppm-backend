package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CapacityDtos.PortfolioCapacity;
import cvt.cv.ppmbackend.dto.CommitteeDecisionDtos.*;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
@Transactional
public class CommitteeDecisionService {

    /** Demandas prontas para entrar num cenário mas ainda sem decisão. */
    private static final Set<DemandPlanningStatus> ELIGIBLE_STATUSES = Set.of(PRIORITIZED);

    private final DemandPlanningCycleRepository planningCycles;
    private final StrategicPlanRepository strategicPlans;
    private final DemandRequirementsResolver profileRequirements;
    private final DemandDependencyRepository dependencies;
    private final CapacityCalculator capacityCalculator;

    public CommitteeDecisionService(
            DemandPlanningCycleRepository planningCycles,
            StrategicPlanRepository strategicPlans,
            DemandRequirementsResolver profileRequirements,
            DemandDependencyRepository dependencies,
            CapacityCalculator capacityCalculator) {
        this.planningCycles = planningCycles;
        this.strategicPlans = strategicPlans;
        this.profileRequirements = profileRequirements;
        this.dependencies = dependencies;
        this.capacityCalculator = capacityCalculator;
    }

    public DecisionDashboardResponse dashboard(UUID strategicPlanId) {
        StrategicPlan plan = strategicPlans.findById(strategicPlanId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plano estratégico não encontrado: " + strategicPlanId));

        List<DemandPlanningCycle> planCycles = planningCycles.findAllByStrategicPlan_Id(strategicPlanId);
        List<DemandPlanningCycle> scenarioCycles = planCycles.stream().filter(this::isInScenario).toList();
        List<DemandPlanningCycle> pendingCycles = planCycles.stream()
                .filter(this::isPendingDecision).toList();
        Set<UUID> scenarioDemandIds = scenarioCycles.stream()
                .map(c -> c.getDemand().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<DemandProfileRequirement> requirements = profileRequirements.forDemands(
                scenarioCycles.stream().map(DemandPlanningCycle::getDemand).toList());
        List<DemandDependency> blockingDependencies = scenarioDemandIds.isEmpty()
                ? List.of()
                : dependencies.findByDemand_IdIn(scenarioDemandIds).stream()
                        .filter(dependency -> dependency.getType() == DemandDependencyType.BLOCKING)
                        .toList();

        int deliberated = (int) planCycles.stream()
                .filter(c -> c.getStatus() == IN_PLAN || c.getStatus() == BACKLOG)
                .count();
        int pending = pendingCycles.size();
        int openConditions = (int) planCycles.stream()
                .filter(cycle -> cycle.getStatus() == IN_REVIEW)
                .count();
        BigDecimal budget = scenarioCycles.stream()
                .map(c -> c.getDemand().getEstimatedBudget())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pendingBudget = pendingCycles.stream()
                .map(c -> c.getDemand().getEstimatedBudget())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        CapacityCalculation capacity = capacity(requirements);
        int blockedDemands = (int) blockingDependencies.stream()
                .map(dependency -> dependency.getDemand().getId())
                .distinct()
                .count();
        int criticalDependencies = blockingDependencies.size();

        List<AttentionPoint> attentionPoints = attentionPoints(
                capacity, blockedDemands, criticalDependencies, openConditions);
        CapacitySummary capacitySummary = new CapacitySummary(
                capacity.available(), capacity.required(), capacity.coveragePercentage());

        return new DecisionDashboardResponse(
                new StrategicPlanDecisionSummary(
                        plan.getId(), plan.getName(), plan.getStartYear(), plan.getEndYear(),
                        plan.getStatus().name(), plan.getStatus().label(), deliberated, planCycles.size(),
                        percentage(deliberated, planCycles.size())),
                new ScenarioDecisionSummary(
                        scenarioCycles.size(), budget, capacitySummary, pending, openConditions),
                new DecisionOverview(
                        pendingBudget, capacitySummary, capacity.deficitProfiles(), blockedDemands,
                        criticalDependencies),
                attentionPoints);
    }

    private boolean isInScenario(DemandPlanningCycle cycle) {
        return cycle.getStatus() == IN_PLAN;
    }

    private boolean isPendingDecision(DemandPlanningCycle cycle) {
        return ELIGIBLE_STATUSES.contains(cycle.getStatus());
    }

    /**
     * Mesmo cálculo mensal da análise de plano/cenário: necessário = carga no pico de cada perfil;
     * perfis em défice = perfis fora dos limites (estado diferente de OK).
     */
    private CapacityCalculation capacity(List<DemandProfileRequirement> requirements) {
        PortfolioCapacity summary = capacityCalculator.summarize(requirements);
        return new CapacityCalculation(summary.totalAvailableCapacity(), summary.totalRequiredLoad(),
                summary.coveragePercentage(), summary.profilesOutOfBounds());
    }

    private List<AttentionPoint> attentionPoints(
            CapacityCalculation capacity,
            int blockedDemands,
            int criticalDependencies,
            int openConditions) {
        List<AttentionPoint> points = new ArrayList<>();
        if (capacity.deficitProfiles() > 0) {
            points.add(new AttentionPoint(
                    "CAPACITY_DEFICIT", "DANGER",
                    capacity.deficitProfiles() + " perfil(is) fora dos limites de capacidade",
                    capacity.deficitProfiles()));
        }
        if (blockedDemands > 0) {
            points.add(new AttentionPoint(
                    "BLOCKING_DEPENDENCIES", "DANGER",
                    blockedDemands + " demanda(s) possuem " + criticalDependencies
                            + " dependência(s) bloqueante(s)",
                    blockedDemands));
        }
        if (openConditions > 0) {
            points.add(new AttentionPoint(
                    "OPEN_CONDITIONS", "WARNING",
                    openConditions + " aprovação(ões) condicional(is) permanecem abertas",
                    openConditions));
        }
        return List.copyOf(points);
    }

    private int percentage(int value, int total) {
        return total == 0 ? 0 : (int) Math.round(value * 100.0 / total);
    }

    private record CapacityCalculation(
            BigDecimal available,
            BigDecimal required,
            BigDecimal coveragePercentage,
            int deficitProfiles) {
    }

    private String norm(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String actor(String value) {
        return value == null || value.isBlank() ? "system" : value;
    }
}
