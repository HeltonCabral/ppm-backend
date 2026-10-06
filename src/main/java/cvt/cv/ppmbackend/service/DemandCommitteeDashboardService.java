package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.CommitteeDashboardResponse;
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
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
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
    private final DemandProfileRequirementRepository profileRequirements;
    private final DemandDependencyRepository dependencies;

    public DemandCommitteeDashboardService(StrategicPlanRepository strategicPlans,
            DemandPlanningCycleRepository planningCycles,
            DemandProfileRequirementRepository profileRequirements,
            DemandDependencyRepository dependencies) {
        this.strategicPlans = strategicPlans;
        this.planningCycles = planningCycles;
        this.profileRequirements = profileRequirements;
        this.dependencies = dependencies;
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
        List<DemandProfileRequirement> attentionRequirements = attentionDemandIds.isEmpty()
                ? List.of() : profileRequirements.findByDemand_IdIn(attentionDemandIds);

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

        Set<UUID> scenarioDemandIds = scenarioCycles.stream().map(c -> c.getDemand().getId())
                .collect(Collectors.toSet());
        List<DemandProfileRequirement> scenarioRequirements = scenarioDemandIds.isEmpty()
                ? List.of() : profileRequirements.findByDemand_IdIn(scenarioDemandIds);
        BigDecimal expectedCapacityPercentage = coveragePercentage(scenarioRequirements);

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

    private PortfolioAttentionPoint restrictedCapacityPoint(List<DemandProfileRequirement> liveRequirements) {
        List<ProfileDeficit> deficits = deficitProfiles(liveRequirements);
        Set<UUID> deficitProfileIds = deficits.stream().map(d -> d.profile().getId()).collect(Collectors.toSet());
        int impactedDemands = (int) liveRequirements.stream()
                .filter(r -> deficitProfileIds.contains(r.getProfile().getId()))
                .map(r -> r.getDemand().getId())
                .distinct()
                .count();

        List<String> topNames = deficits.stream().limit(3).map(d -> d.profile().getName()).toList();
        String detail = topNames.isEmpty()
                ? "Sem perfis em défice de capacidade"
                : "Principais perfis em défice: " + joinWithAnd(topNames);

        return new PortfolioAttentionPoint("CAPACITY_RESTRICTED", impactedDemands > 0 ? "DANGER" : "OK",
                "Capacidade restrita", impactedDemands, detail);
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

    private List<ProfileDeficit> deficitProfiles(List<DemandProfileRequirement> requirements) {
        Map<UUID, BigDecimal> requiredByProfile = new HashMap<>();
        Map<UUID, Profile> profilesById = new HashMap<>();
        for (DemandProfileRequirement requirement : requirements) {
            Profile profile = requirement.getProfile();
            profilesById.put(profile.getId(), profile);
            BigDecimal required = BigDecimal.valueOf(requirement.getRequiredQuantity());
            requiredByProfile.merge(profile.getId(), required, BigDecimal::add);
        }

        List<ProfileDeficit> deficits = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : requiredByProfile.entrySet()) {
            Profile profile = profilesById.get(entry.getKey());
            BigDecimal available = profile.getAvailableCapacity() == null
                    ? BigDecimal.ZERO : BigDecimal.valueOf(profile.getAvailableCapacity());
            BigDecimal required = entry.getValue();
            if (required.compareTo(available) > 0) {
                deficits.add(new ProfileDeficit(profile, required.subtract(available)));
            }
        }
        deficits.sort(Comparator.comparing(ProfileDeficit::deficit).reversed());
        return deficits;
    }

    private BigDecimal coveragePercentage(List<DemandProfileRequirement> requirements) {
        Map<UUID, BigDecimal> requiredByProfile = new HashMap<>();
        Map<UUID, Profile> profilesById = new HashMap<>();
        for (DemandProfileRequirement requirement : requirements) {
            Profile profile = requirement.getProfile();
            profilesById.put(profile.getId(), profile);
            BigDecimal required = BigDecimal.valueOf(requirement.getRequiredQuantity());
            requiredByProfile.merge(profile.getId(), required, BigDecimal::add);
        }
        BigDecimal available = profilesById.values().stream()
                .map(Profile::getAvailableCapacity)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal required = requiredByProfile.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (required.signum() == 0) {
            return null;
        }
        return available.multiply(BigDecimal.valueOf(100)).divide(required, 2, RoundingMode.HALF_UP);
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

    private record ProfileDeficit(Profile profile, BigDecimal deficit) {
    }
}
