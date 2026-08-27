package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.CapacityAnalysisResponse;
import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.CompetingDemand;
import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.ProfileCapacityAnalysis;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DemandCapacityAnalysisService {

    private static final List<DemandPlanningStatus> LIVE_STATUSES = List.of(
            DemandPlanningStatus.DRAFT, DemandPlanningStatus.IN_ANALYSIS, DemandPlanningStatus.IN_PRIORITIZATION,
            DemandPlanningStatus.AWAITING_COMMITTEE, DemandPlanningStatus.APPROVED,
            DemandPlanningStatus.CONDITIONALLY_APPROVED);

    private final DemandPlanningCycleRepository planningCycles;
    private final DemandPlanningCycleService planningCycleService;
    private final DemandProfileRequirementRepository profileRequirements;

    public DemandCapacityAnalysisService(DemandPlanningCycleRepository planningCycles,
            DemandPlanningCycleService planningCycleService,
            DemandProfileRequirementRepository profileRequirements) {
        this.planningCycles = planningCycles;
        this.planningCycleService = planningCycleService;
        this.profileRequirements = profileRequirements;
    }

    @Transactional(readOnly = true)
    public CapacityAnalysisResponse analyze(UUID demandId, UUID strategicPlanId) {
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(demandId, strategicPlanId);

        List<DemandProfileRequirement> ownRequirements =
                profileRequirements.findByDemand_IdOrderByProfile_NameAsc(demandId);
        if (ownRequirements.isEmpty()) {
            return new CapacityAnalysisResponse("OK", null, List.of());
        }

        List<DemandPlanningCycle> liveCycles =
                planningCycles.findAllByStrategicPlan_IdAndStatusIn(strategicPlanId, LIVE_STATUSES);
        Set<UUID> otherDemandIds = liveCycles.stream()
                .map(c -> c.getDemand().getId())
                .filter(id -> !id.equals(demandId))
                .collect(Collectors.toSet());

        List<DemandProfileRequirement> otherRequirements = otherDemandIds.isEmpty()
                ? List.of()
                : profileRequirements.findByDemand_IdIn(otherDemandIds);
        Map<UUID, List<DemandProfileRequirement>> otherByProfile = otherRequirements.stream()
                .collect(Collectors.groupingBy(r -> r.getProfile().getId()));

        List<ProfileCapacityAnalysis> profiles = new ArrayList<>();
        for (DemandProfileRequirement requirement : ownRequirements) {
            profiles.add(analyzeProfile(requirement, otherByProfile));
        }

        String status = globalStatus(profiles);
        return new CapacityAnalysisResponse(status, mainConflict(profiles, status), List.copyOf(profiles));
    }

    private ProfileCapacityAnalysis analyzeProfile(DemandProfileRequirement requirement,
            Map<UUID, List<DemandProfileRequirement>> otherByProfile) {
        Profile profile = requirement.getProfile();
        List<CompetingDemand> competingDemands = otherByProfile
                .getOrDefault(profile.getId(), List.of()).stream()
                .map(this::toCompetingDemand)
                .toList();

        Integer limit = profile.getSimultaneousDemandCapacity();
        int used = competingDemands.size();
        boolean quantityDeficit = hasQuantityDeficit(profile.getAvailableCapacity(), limit,
                requirement.getRequiredQuantity(), requirement.getAllocationPercentage(), competingDemands);

        return new ProfileCapacityAnalysis(
                profile.getId(), profile.getName(), profile.getDirectionCode(), profile.isActive(),
                requirement.getRequiredQuantity(), requirement.getAllocationPercentage(),
                profile.getAvailableCapacity(), limit, used, situation(used, limit, quantityDeficit),
                competingDemands);
    }

    private boolean hasQuantityDeficit(Integer availableCapacity, Integer simultaneousLimit,
            Integer ownRequiredQuantity, Integer ownAllocationPercentage, List<CompetingDemand> competingDemands) {
        if (availableCapacity == null || simultaneousLimit == null) {
            return false;
        }
        BigDecimal totalCapacity = BigDecimal.valueOf(availableCapacity).multiply(BigDecimal.valueOf(simultaneousLimit));
        BigDecimal totalRequired = weighted(ownRequiredQuantity, ownAllocationPercentage);
        for (CompetingDemand competing : competingDemands) {
            totalRequired = totalRequired.add(weighted(competing.requiredQuantity(), competing.allocationPercentage()));
        }
        return totalRequired.compareTo(totalCapacity) > 0;
    }

    private BigDecimal weighted(Integer requiredQuantity, Integer allocationPercentage) {
        return BigDecimal.valueOf(requiredQuantity)
                .multiply(BigDecimal.valueOf(allocationPercentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private CompetingDemand toCompetingDemand(DemandProfileRequirement requirement) {
        Demand demand = requirement.getDemand();
        LocalDate periodStart = demand.getPlannedStartDate();
        LocalDate periodEnd = demand.getDesiredDate();
        if (periodStart == null || periodEnd == null) {
            periodStart = null;
            periodEnd = null;
        }
        return new CompetingDemand(demand.getId(), demand.getCode(), demand.getTitle(),
                requirement.getRequiredQuantity(), requirement.getAllocationPercentage(), periodStart, periodEnd);
    }

    private String situation(int used, Integer limit, boolean quantityDeficit) {
        if (quantityDeficit) {
            return "RESTRITA";
        }
        if (limit == null) {
            return null;
        }
        if (used >= limit) {
            return "RESTRITA";
        }
        if (used == limit - 1) {
            return "CONDICIONADA";
        }
        return "OK";
    }

    private String globalStatus(List<ProfileCapacityAnalysis> profiles) {
        if (profiles.stream().anyMatch(p -> "RESTRITA".equals(p.situation()))) {
            return "RESTRITA";
        }
        if (profiles.stream().anyMatch(p -> "CONDICIONADA".equals(p.situation()))) {
            return "CONDICIONADA";
        }
        return "OK";
    }

    private String mainConflict(List<ProfileCapacityAnalysis> profiles, String status) {
        if ("OK".equals(status)) {
            return null;
        }
        return profiles.stream()
                .filter(p -> status.equals(p.situation()))
                .findFirst()
                .map(p -> conflictMessage(p, status))
                .orElse(null);
    }

    private String conflictMessage(ProfileCapacityAnalysis profile, String status) {
        if ("CONDICIONADA".equals(status)) {
            return profile.profileName() + " está próximo do limite de capacidade simultânea.";
        }
        boolean quantityDeficit = hasQuantityDeficit(profile.availableCapacity(), profile.simultaneousLimit(),
                profile.requiredQuantity(), profile.allocationPercentage(), profile.competingDemands());
        return quantityDeficit
                ? profile.profileName() + " não tem capacidade suficiente para a quantidade solicitada."
                : profile.profileName() + " excede a capacidade para demandas simultâneas.";
    }
}
