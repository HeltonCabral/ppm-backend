package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CommitteeDecisionDtos.*;
import cvt.cv.ppmbackend.dto.DemandCommitteeDecisionDtos.DecisionCommand;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCommitteeDecision;
import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.CommitteeDecision;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandCommitteeDecisionRepository;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
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

    private static final Set<CommitteeDecision> REQUIRE_JUSTIFICATION = Set.of(
            CommitteeDecision.CONDITIONALLY_APPROVED, CommitteeDecision.REQUEST_REVISION,
            CommitteeDecision.BACKLOG);

    private static final Set<DemandPlanningStatus> ELIGIBLE_STATUSES = Set.of(
            IN_PRIORITIZATION, AWAITING_COMMITTEE);

    private final DemandPlanningCycleRepository planningCycles;
    private final DemandPlanningCycleService planningCycleService;
    private final StrategicPlanRepository strategicPlans;
    private final DemandProfileRequirementRepository profileRequirements;
    private final DemandDependencyRepository dependencies;
    private final DemandCommitteeDecisionRepository decisions;

    public CommitteeDecisionService(
            DemandPlanningCycleRepository planningCycles,
            DemandPlanningCycleService planningCycleService,
            StrategicPlanRepository strategicPlans,
            DemandProfileRequirementRepository profileRequirements,
            DemandDependencyRepository dependencies, DemandCommitteeDecisionRepository decisions) {
        this.planningCycles = planningCycles;
        this.planningCycleService = planningCycleService;
        this.strategicPlans = strategicPlans;
        this.profileRequirements = profileRequirements;
        this.dependencies = dependencies;
        this.decisions = decisions;
    }

    public DecisionDashboardResponse dashboard(UUID strategicPlanId) {
        StrategicPlan plan = strategicPlans.findById(strategicPlanId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plano estratégico não encontrado: " + strategicPlanId));

        List<DemandPlanningCycle> planCycles = planningCycles.findAllByStrategicPlan_Id(strategicPlanId);
        List<DemandPlanningCycle> scenarioCycles = planCycles.stream().filter(this::isInScenario).toList();
        Set<UUID> decidedCycleIds = decisions.findDecidedCycleIdsByStrategicPlan(strategicPlanId);
        List<DemandPlanningCycle> pendingCycles = planCycles.stream()
                .filter(cycle -> isPendingDecision(cycle, decidedCycleIds)).toList();
        Set<UUID> scenarioDemandIds = scenarioCycles.stream()
                .map(c -> c.getDemand().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<DemandProfileRequirement> requirements = scenarioDemandIds.isEmpty()
                ? List.of()
                : profileRequirements.findByDemand_IdIn(scenarioDemandIds);
        List<DemandDependency> blockingDependencies = scenarioDemandIds.isEmpty()
                ? List.of()
                : dependencies.findByDemand_IdIn(scenarioDemandIds).stream()
                        .filter(dependency -> dependency.getType() == DemandDependencyType.BLOCKING)
                        .toList();

        int deliberated = (int) planCycles.stream().filter(c -> decidedCycleIds.contains(c.getId())).count();
        int pending = pendingCycles.size();
        int openConditions = (int) scenarioCycles.stream()
                .filter(cycle -> cycle.getStatus() == CONDITIONALLY_APPROVED)
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

    @Transactional
    public BulkDeliberationResponse deliberate(BulkDeliberationRequest req, String actorId) {
        validateDecision(req);

        List<DemandPlanningCycle> eligibleCycles = findEligibleCycles(req.strategicPlanId(), req.filters());

        List<DemandDecisionInfo> processed = new ArrayList<>();
        List<SkippedDemandInfo> skipped = new ArrayList<>();

        for (DemandPlanningCycle cycle : eligibleCycles) {
            Demand demand = cycle.getDemand();
            try {
                if (isEligibleForDeliberation(cycle)) {
                    processed.add(applyDecisionAndMap(cycle, req, actorId));
                } else {
                    skipped.add(new SkippedDemandInfo(
                            demand.getId(),
                            demand.getCode(),
                            demand.getTitle(),
                            "Status não elegível para deliberação: " + cycle.getStatus()));
                }
            } catch (Exception e) {
                skipped.add(new SkippedDemandInfo(
                        demand.getId(),
                        demand.getCode(),
                        demand.getTitle(),
                        "Erro ao processar: " + e.getMessage()));
            }
        }

        return new BulkDeliberationResponse(
                eligibleCycles.size(),
                processed.size(),
                skipped.size(),
                processed,
                skipped);
    }

    private void validateDecision(BulkDeliberationRequest req) {
        CommitteeDecision decision = CommitteeDecision.from(req.decision());
        if (decision == null) {
            throw new BadRequestException("Decisão inválida: " + req.decision() + ". Valores permitidos: "
                    + Arrays.stream(CommitteeDecision.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
        if (REQUIRE_JUSTIFICATION.contains(decision)
                && (req.justification() == null || req.justification().isBlank())) {
            throw new BadRequestException("justification é obrigatória quando decision = " + decision);
        }
    }

    private List<DemandPlanningCycle> findEligibleCycles(UUID strategicPlanId, BulkDeliberationFilters filters) {
        Specification<DemandPlanningCycle> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                root.get("status").in(ELIGIBLE_STATUSES)
        );

        if (filters.scoreMin() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.greaterThanOrEqualTo(r.get("scoreTotal"), filters.scoreMin()));
        }

        if (filters.scoreMax() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.lessThanOrEqualTo(r.get("scoreTotal"), filters.scoreMax()));
        }

        if (filters.committeeId() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(r.get("responsibleCommittee").get("id"), filters.committeeId()));
        }

        if (filters.directionCode() != null && !filters.directionCode().isBlank()) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(cb.upper(r.get("demand").get("directionCode")),
                            filters.directionCode().trim().toUpperCase(Locale.ROOT)));
        }

        if (filters.urgency() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(cb.upper(r.get("demand").get("urgency")), norm(filters.urgency())));
        }

        if (filters.riskLevel() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(cb.upper(r.get("riskStatus")), norm(filters.riskLevel())));
        }

        return planningCycles.findAll(spec);
    }

    private boolean isEligibleForDeliberation(DemandPlanningCycle cycle) {
        return ELIGIBLE_STATUSES.contains(cycle.getStatus());
    }

    private DemandDecisionInfo applyDecisionAndMap(DemandPlanningCycle cycle, BulkDeliberationRequest req,
            String actorId) {
        DemandPlanningStatus previousStatus = cycle.getStatus();

        DemandCommitteeDecision recorded = planningCycleService.applyCommitteeDecision(cycle,
                new DecisionCommand(CommitteeDecision.from(req.decision()), req.justification(), req.condition(),
                        req.meetingReference(), actor(actorId), actor(actorId)));

        DemandPlanningCycle updated = recorded.getPlanningCycle();
        Demand demand = updated.getDemand();
        return new DemandDecisionInfo(
                demand.getId(),
                demand.getCode(),
                demand.getTitle(),
                previousStatus,
                updated.getStatus(),
                recorded.getDecision().name(),
                recorded.getConditions(),
                recorded.getJustification(),
                recorded.getDecisionAt(),
                recorded.getDecidedBy());
    }

    private boolean isInScenario(DemandPlanningCycle cycle) {
        return cycle.getStatus() == APPROVED || cycle.getStatus() == CONDITIONALLY_APPROVED;
    }

    private boolean isPendingDecision(DemandPlanningCycle cycle, Set<UUID> decidedCycleIds) {
        return ELIGIBLE_STATUSES.contains(cycle.getStatus()) && !decidedCycleIds.contains(cycle.getId());
    }

    private CapacityCalculation capacity(List<DemandProfileRequirement> requirements) {
        Map<UUID, BigDecimal> requiredByProfile = new HashMap<>();
        Map<UUID, Profile> profilesById = new HashMap<>();

        for (DemandProfileRequirement requirement : requirements) {
            Profile profile = requirement.getProfile();
            profilesById.put(profile.getId(), profile);
            BigDecimal required = BigDecimal.valueOf(requirement.getRequiredQuantity())
                    .multiply(BigDecimal.valueOf(requirement.getAllocationPercentage()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            requiredByProfile.merge(profile.getId(), required, BigDecimal::add);
        }

        BigDecimal available = profilesById.values().stream()
                .map(Profile::getAvailableCapacity)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal required = requiredByProfile.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int deficitProfiles = (int) requiredByProfile.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.valueOf(
                        Optional.ofNullable(profilesById.get(entry.getKey()).getAvailableCapacity()).orElse(0))) > 0)
                .count();
        BigDecimal coverage = required.signum() == 0
                ? null
                : available.multiply(BigDecimal.valueOf(100))
                        .divide(required, 2, RoundingMode.HALF_UP);

        return new CapacityCalculation(available, required, coverage, deficitProfiles);
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
                    capacity.deficitProfiles() + " perfil(is) apresentam défice de capacidade",
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
