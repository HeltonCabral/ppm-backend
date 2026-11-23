package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CapacityDtos.ProfileUtilization;
import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.CapacityAnalysisResponse;
import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.CompetingDemand;
import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.ProfileCapacityAnalysis;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandPhaseRef;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.enums.CapacityStatus;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Análise de capacidade orientada a Demandas. Delega o cálculo por perfil e por mês em
 * {@link CapacityCalculator} e devolve a agregação por Demanda: o pior estado entre os perfis
 * que ela requer.
 */
@Service
@Transactional
public class DemandCapacityAnalysisService {

    static final List<DemandPlanningStatus> LIVE_STATUSES = List.of(
            DemandPlanningStatus.DRAFT, DemandPlanningStatus.IN_ANALYSIS, DemandPlanningStatus.IN_REVIEW,
            DemandPlanningStatus.IN_PRIORITIZATION, DemandPlanningStatus.PRIORITIZED,
            DemandPlanningStatus.IN_PLAN);

    private final DemandPlanningCycleRepository planningCycles;
    private final DemandPlanningCycleService planningCycleService;
    private final DemandRequirementsResolver requirements;
    private final CapacityCalculator calculator;

    public DemandCapacityAnalysisService(DemandPlanningCycleRepository planningCycles,
            DemandPlanningCycleService planningCycleService,
            DemandRequirementsResolver requirements,
            CapacityCalculator calculator) {
        this.planningCycles = planningCycles;
        this.planningCycleService = planningCycleService;
        this.requirements = requirements;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public CapacityAnalysisResponse analyze(UUID demandId, UUID strategicPlanId) {
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(demandId, strategicPlanId);
        Demand demand = cycle.getDemand();

        if (demand.getPlannedStartDate() == null || demand.getDesiredDate() == null) {
            return new CapacityAnalysisResponse(CapacityStatus.NOT_CALCULATED, null, null, null,
                    "Sem início previsto/duração: calcule a complexidade da demanda.", List.of());
        }

        List<DemandProfileRequirement> ownRequirements = requirements.forDemand(demand);
        if (ownRequirements.isEmpty()) {
            return new CapacityAnalysisResponse(CapacityStatus.OK, null, null, null, null, List.of());
        }

        List<DemandProfileRequirement> allRequirements = liveRequirements(strategicPlanId);
        Map<UUID, ProfileUtilization> utilizations = calculator.profileUtilizations(allRequirements);

        Map<UUID, List<DemandProfileRequirement>> othersByProfile = allRequirements.stream()
                .filter(r -> !r.getDemand().getId().equals(demandId))
                .collect(Collectors.groupingBy(r -> r.getProfile().getId()));

        List<ProfileCapacityAnalysis> profiles = new ArrayList<>();
        for (DemandProfileRequirement requirement : ownRequirements) {
            UUID profileId = requirement.getProfile().getId();
            Set<YearMonth> months = calculator.requirementMonths(requirement);
            ProfileUtilization utilization = calculator.scopeToMonths(utilizations.get(profileId), months);
            List<CompetingDemand> competing = competingDemands(
                    othersByProfile.getOrDefault(profileId, List.of()), months);
            profiles.add(buildProfileAnalysis(requirement, utilization, competing));
        }
        // Ordena por nome do perfil e, em empate, pela menor sequence das fases da entrada.
        profiles.sort(Comparator.comparing(ProfileCapacityAnalysis::profileName,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DemandCapacityAnalysisService::minPhaseSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        CapacityStatus overall = CapacityStatus.OK;
        for (ProfileCapacityAnalysis profile : profiles) {
            overall = CapacityStatus.worst(overall, profile.status());
        }
        ProfileCapacityAnalysis peakProfile = pickPeak(profiles);
        return new CapacityAnalysisResponse(
                overall,
                peakProfile == null ? null : peakProfile.peakUtilization(),
                peakProfile == null ? null : peakProfile.criticalPeriod(),
                mainConflict(profiles, overall),
                null,
                List.copyOf(profiles));
    }

    /**
     * Calcula em lote o estado de capacidade de cada Demanda viva do plano. Devolve mapa
     * {@code demandId → CapacityStatus}, usado pela listagem de cenário para preencher a coluna
     * "Capacidade" sem novas consultas.
     */
    @Transactional(readOnly = true)
    public Map<UUID, CapacityStatus> statusesForStrategicPlan(UUID strategicPlanId) {
        List<DemandProfileRequirement> allRequirements = liveRequirements(strategicPlanId);
        if (allRequirements.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProfileUtilization> utilizations = calculator.profileUtilizations(allRequirements);
        Map<UUID, Set<UUID>> profileIdsByDemand = allRequirements.stream()
                .collect(Collectors.groupingBy(r -> r.getDemand().getId(),
                        Collectors.mapping(r -> r.getProfile().getId(), Collectors.toSet())));

        Map<UUID, CapacityStatus> statuses = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : profileIdsByDemand.entrySet()) {
            statuses.put(entry.getKey(), calculator.demandStatus(entry.getValue(), utilizations));
        }
        return statuses;
    }

    private List<DemandProfileRequirement> liveRequirements(UUID strategicPlanId) {
        List<DemandPlanningCycle> liveCycles =
                planningCycles.findAllByStrategicPlan_IdAndStatusIn(strategicPlanId, LIVE_STATUSES);
        if (liveCycles.isEmpty()) {
            return List.of();
        }
        return requirements.forDemands(liveCycles.stream().map(DemandPlanningCycle::getDemand).toList());
    }

    private List<CompetingDemand> competingDemands(List<DemandProfileRequirement> reqs, Set<YearMonth> months) {
        Map<UUID, Demand> demandsById = new LinkedHashMap<>();
        Map<UUID, BigDecimal> fteByDemand = new LinkedHashMap<>();
        for (DemandProfileRequirement r : reqs) {
            if (!intersects(calculator.requirementMonths(r), months)) {
                continue;
            }
            Demand demand = r.getDemand();
            demandsById.putIfAbsent(demand.getId(), demand);
            fteByDemand.merge(demand.getId(), r.getEquivalentLoad(), BigDecimal::add);
        }
        List<CompetingDemand> out = new ArrayList<>();
        for (Demand demand : demandsById.values()) {
            out.add(new CompetingDemand(demand.getId(), demand.getCode(), demand.getTitle(),
                    fteByDemand.get(demand.getId()).setScale(2, java.math.RoundingMode.HALF_UP),
                    demand.getPlannedStartDate(), demand.getDesiredDate()));
        }
        return out;
    }

    private ProfileCapacityAnalysis buildProfileAnalysis(DemandProfileRequirement requirement,
            ProfileUtilization utilization, List<CompetingDemand> competing) {
        List<DemandPhaseRef> phases = phasesOf(requirement);
        BigDecimal requiredFte = requirement.getEquivalentLoad().setScale(2, java.math.RoundingMode.HALF_UP);
        if (utilization == null) {
            return new ProfileCapacityAnalysis(
                    requirement.getId(), requirement.getProfile().getId(), requirement.getProfile().getName(),
                    requirement.getProfile().getDirectionCode(), requirement.getProfile().isActive(),
                    requiredFte,
                    requirement.getProfile().getAvailableCapacity(),
                    requirement.getProfile().getSimultaneousDemandCapacity(),
                    CapacityStatus.NOT_CONFIGURED, null, null, null, 0, List.of(), competing, phases);
        }
        return new ProfileCapacityAnalysis(
                requirement.getId(), utilization.profileId(), utilization.profileName(), utilization.directionCode(),
                utilization.active(),
                requiredFte,
                utilization.availableCapacity(), utilization.simultaneousDemandCapacity(),
                utilization.status(), utilization.peakUtilization(), utilization.criticalPeriod(),
                utilization.requiredLoadAtPeak(), utilization.simultaneousDemandsAtPeak(),
                utilization.periods(), competing, phases);
    }

    private List<DemandPhaseRef> phasesOf(DemandProfileRequirement requirement) {
        return requirement.getPhases().stream()
                .sorted(Comparator.comparing(PhaseDefinition::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new DemandPhaseRef(p.getId(), p.getCode(), p.getName(), p.getSequence()))
                .toList();
    }

    private static Integer minPhaseSequence(ProfileCapacityAnalysis profile) {
        return profile.phases().stream()
                .map(DemandPhaseRef::phaseSequence)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean intersects(Set<YearMonth> a, Set<YearMonth> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<YearMonth> smaller = a.size() <= b.size() ? a : b;
        Set<YearMonth> larger = smaller == a ? b : a;
        for (YearMonth m : smaller) {
            if (larger.contains(m)) {
                return true;
            }
        }
        return false;
    }

    private ProfileCapacityAnalysis pickPeak(List<ProfileCapacityAnalysis> profiles) {
        ProfileCapacityAnalysis peak = null;
        for (ProfileCapacityAnalysis p : profiles) {
            if (p.peakUtilization() == null) {
                continue;
            }
            if (peak == null || peak.peakUtilization() == null
                    || p.peakUtilization().compareTo(peak.peakUtilization()) > 0) {
                peak = p;
            }
        }
        return peak;
    }

    private String mainConflict(List<ProfileCapacityAnalysis> profiles, CapacityStatus overall) {
        if (overall == CapacityStatus.OK) {
            return null;
        }
        return profiles.stream()
                .filter(p -> p.status() == overall)
                .findFirst()
                .map(p -> conflictMessage(p, overall))
                .orElse(null);
    }

    private String conflictMessage(ProfileCapacityAnalysis profile, CapacityStatus status) {
        return switch (status) {
            case NOT_CONFIGURED -> profile.profileName() + " não tem capacidade parametrizada.";
            case CRITICAL -> profile.profileName() + " atinge utilização crítica ("
                    + formatPeak(profile) + ").";
            case RESTRICTED -> profile.profileName() + " excede a capacidade disponível ("
                    + formatPeak(profile) + ").";
            case CONDITIONED -> profile.profileName() + " opera perto do limite de capacidade ("
                    + formatPeak(profile) + ").";
            case OK, NOT_CALCULATED -> null;
        };
    }

    private String formatPeak(ProfileCapacityAnalysis profile) {
        BigDecimal peak = profile.peakUtilization();
        String peakStr = peak == null ? "s/ pico" : peak + "%";
        return profile.criticalPeriod() == null ? peakStr : peakStr + " em " + profile.criticalPeriod();
    }
}
