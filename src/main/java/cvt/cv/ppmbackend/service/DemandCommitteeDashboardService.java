package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CapacityDtos.ProfileUtilization;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.CommitteeDashboardResponse;
import cvt.cv.ppmbackend.enums.CapacityStatus;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.PortfolioAttentionPoint;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.ScenarioSummary;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.StrategicPlanHeader;
import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
@Transactional
public class DemandCommitteeDashboardService {

    private static final Set<DemandPlanningStatus> ELIGIBLE_STATUSES = Set.of(PRIORITIZED);
    private static final int CRITICAL_DEADLINE_DAYS = 90;
    private static final int TOP_DEPENDENCIES_RANK = 20;
    private static final int TOP_BACKLOG_RANK = 30;

    private static final Map<DemandNature, String> NATURE_LABELS = Map.of(
            DemandNature.REGULATORY, "regulatórias",
            DemandNature.LEGAL, "legais",
            DemandNature.CONTRACTUAL, "contratuais",
            DemandNature.CORPORATE_MANDATORY, "corporativas",
            DemandNature.OTHER_MANDATORY, "outras obrigatórias");

    private final StrategicPlanRepository strategicPlans;
    private final DemandPlanningCycleRepository planningCycles;
    private final DemandRequirementsResolver profileRequirements;
    private final DemandDependencyRepository dependencies;
    private final CapacityCalculator capacityCalculator;

    public DemandCommitteeDashboardService(StrategicPlanRepository strategicPlans,
            DemandPlanningCycleRepository planningCycles,
            DemandRequirementsResolver profileRequirements,
            DemandDependencyRepository dependencies,
            CapacityCalculator capacityCalculator) {
        this.strategicPlans = strategicPlans;
        this.planningCycles = planningCycles;
        this.profileRequirements = profileRequirements;
        this.dependencies = dependencies;
        this.capacityCalculator = capacityCalculator;
    }

    @Transactional(readOnly = true)
    public CommitteeDashboardResponse dashboard(UUID strategicPlanId) {
        StrategicPlan plan = strategicPlans.findById(strategicPlanId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plano estratégico não encontrado: " + strategicPlanId));

        List<DemandPlanningCycle> planCycles = planningCycles.findAllByStrategicPlan_Id(strategicPlanId);
        List<DemandPlanningCycle> attentionCycles = planCycles.stream()
                .filter(c -> c.getStatus() == PRIORITIZED)
                .toList();
        List<DemandPlanningCycle> scenarioCycles = planCycles.stream()
                .filter(c -> c.getStatus() == IN_PLAN)
                .toList();

        int deliberated = (int) planCycles.stream()
                .filter(c -> c.getStatus() == IN_PLAN || c.getStatus() == BACKLOG).count();
        StrategicPlanHeader header = new StrategicPlanHeader(
                plan.getId(), plan.getName(), plan.getStartYear(), plan.getEndYear(),
                plan.getStatus().name(), plan.getStatus().label(), deliberated, planCycles.size(),
                percentage(deliberated, planCycles.size()));

        ScenarioSummary scenario = scenarioSummary(planCycles, scenarioCycles);

        Set<UUID> attentionDemandIds = attentionCycles.stream()
                .map(c -> c.getDemand().getId())
                .collect(Collectors.toSet());
        List<DemandProfileRequirement> attentionRequirements = profileRequirements.forDemands(
                attentionCycles.stream().map(DemandPlanningCycle::getDemand).toList());

        List<PortfolioAttentionPoint> attentionPoints = List.of(
                mandatoryDemandsPoint(attentionCycles),
                criticalDeadlinesPoint(attentionCycles),
                restrictedCapacityPoint(attentionRequirements),
                blockingDependenciesPoint(attentionCycles, attentionDemandIds),
                previousBacklogPoint(attentionCycles));

        return new CommitteeDashboardResponse(header, scenario, attentionPoints);
    }

    private ScenarioSummary scenarioSummary(List<DemandPlanningCycle> planCycles,
            List<DemandPlanningCycle> scenarioCycles) {
        BigDecimal requiredBudget = scenarioCycles.stream()
                .map(c -> c.getDemand().getEstimatedBudget())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<DemandProfileRequirement> scenarioRequirements = profileRequirements.forDemands(
                scenarioCycles.stream().map(DemandPlanningCycle::getDemand).toList());
        BigDecimal expectedCapacityPercentage = capacityCalculator.summarize(scenarioRequirements).coveragePercentage();

        int pendingDecisions = (int) planCycles.stream()
                .filter(c -> ELIGIBLE_STATUSES.contains(c.getStatus()))
                .count();
        int openConditions = (int) planCycles.stream()
                .filter(c -> c.getStatus() == IN_REVIEW)
                .count();

        return new ScenarioSummary(scenarioCycles.size(), requiredBudget, expectedCapacityPercentage,
                pendingDecisions, openConditions);
    }

    private PortfolioAttentionPoint mandatoryDemandsPoint(List<DemandPlanningCycle> liveCycles) {
        Map<DemandNature, Long> byNature = liveCycles.stream()
                .map(c -> c.getDemand().getNature())
                .filter(nature -> nature != null && nature != DemandNature.NORMAL)
                .collect(Collectors.groupingBy(nature -> nature, Collectors.counting()));
        int count = byNature.values().stream().mapToInt(Long::intValue).sum();

        String detail = Arrays.stream(DemandNature.values())
                .filter(byNature::containsKey)
                .map(nature -> byNature.get(nature) + " " + NATURE_LABELS.get(nature))
                .collect(Collectors.joining(" • "));
        if (detail.isBlank()) {
            detail = "Nenhuma demanda obrigatória em aberto";
        }

        return new PortfolioAttentionPoint("MANDATORY_DEMANDS", "INFO", "Obrigatórias", count, detail);
    }

    private PortfolioAttentionPoint criticalDeadlinesPoint(List<DemandPlanningCycle> liveCycles) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(CRITICAL_DEADLINE_DAYS);
        int count = (int) liveCycles.stream()
                .filter(c -> c.getDemand().getNature() != null && c.getDemand().getNature() != DemandNature.NORMAL)
                .filter(c -> c.getDemand().getDesiredDate() != null)
                .filter(c -> !c.getDemand().getDesiredDate().isBefore(today)
                        && !c.getDemand().getDesiredDate().isAfter(limit))
                .count();

        String detail = "Obrigatórias com prazo nos próximos " + CRITICAL_DEADLINE_DAYS + " dias";
        return new PortfolioAttentionPoint("CRITICAL_DEADLINES", count > 0 ? "WARNING" : "OK",
                "Prazos críticos", count, detail);
    }

    /**
     * Mesmo cálculo mensal da análise de plano/cenário: perfis fora dos limites (estado diferente de
     * OK) e quantas Demandas os requerem. DANGER a partir de RESTRICTED; só CONDITIONED dá WARNING.
     */
    private PortfolioAttentionPoint restrictedCapacityPoint(List<DemandProfileRequirement> liveRequirements) {
        List<ProfileUtilization> outOfBounds = capacityCalculator.summarize(liveRequirements).outOfBoundsProfiles();
        Set<UUID> outOfBoundsIds = outOfBounds.stream().map(ProfileUtilization::profileId).collect(Collectors.toSet());
        int impactedDemands = (int) liveRequirements.stream()
                .filter(r -> outOfBoundsIds.contains(r.getProfile().getId()))
                .map(r -> r.getDemand().getId())
                .distinct()
                .count();

        List<String> topNames = outOfBounds.stream().limit(3).map(ProfileUtilization::profileName).toList();
        String detail = topNames.isEmpty()
                ? "Sem perfis fora dos limites de capacidade"
                : "Principais perfis fora dos limites: " + joinWithAnd(topNames);
        CapacityStatus worst = outOfBounds.stream().map(ProfileUtilization::status)
                .reduce(CapacityStatus.OK, CapacityStatus::worst);
        String severity = worst == CapacityStatus.OK ? "OK"
                : worst == CapacityStatus.CONDITIONED ? "WARNING" : "DANGER";

        return new PortfolioAttentionPoint("CAPACITY_RESTRICTED", severity, "Capacidade restrita", impactedDemands,
                detail);
    }

    private PortfolioAttentionPoint blockingDependenciesPoint(List<DemandPlanningCycle> liveCycles,
            Set<UUID> liveDemandIds) {
        List<DemandDependency> blocking = liveDemandIds.isEmpty()
                ? List.of()
                : dependencies.findByDemand_IdIn(liveDemandIds).stream()
                        .filter(dependency -> dependency.getType() == DemandDependencyType.BLOCKING)
                        .toList();
        Set<UUID> blockedDemandIds = blocking.stream().map(d -> d.getDemand().getId()).collect(Collectors.toSet());
        Map<UUID, DemandPlanningCycle> cycleByDemandId = liveCycles.stream()
                .collect(Collectors.toMap(c -> c.getDemand().getId(), c -> c, (a, b) -> a));

        int impactingTopRank = (int) blockedDemandIds.stream()
                .map(cycleByDemandId::get)
                .filter(Objects::nonNull)
                .filter(c -> c.getPortfolioRank() != null && c.getPortfolioRank() <= TOP_DEPENDENCIES_RANK)
                .count();

        String detail = "Impactam iniciativas no Top " + TOP_DEPENDENCIES_RANK + " do ranking";
        return new PortfolioAttentionPoint("BLOCKING_DEPENDENCIES", impactingTopRank > 0 ? "DANGER" : "OK",
                "Dependências bloqueantes", impactingTopRank, detail);
    }

    private PortfolioAttentionPoint previousBacklogPoint(List<DemandPlanningCycle> liveCycles) {
        List<DemandPlanningCycle> fromBacklog = liveCycles.stream()
                .filter(DemandPlanningCycle::isCarriedOver)
                .filter(c -> c.getPreviousCycle() != null && c.getPreviousCycle().getStatus() == BACKLOG)
                .toList();
        int topRankCount = (int) fromBacklog.stream()
                .filter(c -> c.getPortfolioRank() != null && c.getPortfolioRank() <= TOP_BACKLOG_RANK)
                .count();

        String detail = fromBacklog.isEmpty()
                ? "Nenhuma demanda regressou do backlog anterior"
                : "Regressaram à priorização. " + topRankCount + " estão no Top " + TOP_BACKLOG_RANK;

        return new PortfolioAttentionPoint("PREVIOUS_BACKLOG", "INFO", "Do backlog anterior",
                fromBacklog.size(), detail);
    }

    private String joinWithAnd(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " e " + items.get(items.size() - 1);
    }

    private int percentage(int value, int total) {
        return total == 0 ? 0 : (int) Math.round(value * 100.0 / total);
    }
}
