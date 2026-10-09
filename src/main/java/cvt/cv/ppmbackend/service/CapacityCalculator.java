package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.CapacityThresholdsProperties;
import cvt.cv.ppmbackend.dto.CapacityDtos.PeriodUtilization;
import cvt.cv.ppmbackend.dto.CapacityDtos.PortfolioCapacity;
import cvt.cv.ppmbackend.dto.CapacityDtos.ProfileUtilization;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.enums.CapacityStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cálculo de utilização de capacidade por perfil e por mês.
 *
 * <p>A carga equivalente é {@code requiredQuantity}. Quando o requisito indica fases
 * ({@link DemandProfileRequirement#getPhases()}), a carga é distribuída apenas pelos meses dessas
 * fases; a janela de cada fase deriva da janela total da Demanda ({@code plannedStartDate} →
 * {@code desiredDate}) fatiada pelas {@code durationWeightPercentage} do {@code PhaseModel}. Sem
 * fases associadas, a carga ocupa toda a janela da Demanda. Demandas sem datas caem num contentor de
 * "não escalonadas" que não conta para a utilização mensal mas fica visível no resultado.
 */
@Component
public class CapacityCalculator {

    private final CapacityThresholdsProperties thresholds;

    public CapacityCalculator(CapacityThresholdsProperties thresholds) {
        this.thresholds = thresholds;
    }

    /** Devolve {@code profileId → ProfileUtilization} para todos os perfis tocados pelos requisitos. */
    public Map<UUID, ProfileUtilization> profileUtilizations(Collection<DemandProfileRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<DemandProfileRequirement>> byProfile = requirements.stream()
                .collect(Collectors.groupingBy(r -> r.getProfile().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<UUID, ProfileUtilization> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<DemandProfileRequirement>> entry : byProfile.entrySet()) {
            Profile profile = entry.getValue().get(0).getProfile();
            out.put(profile.getId(), computeForProfile(profile, entry.getValue()));
        }
        return out;
    }

    /**
     * Agregado de capacidade de um conjunto de requisitos: estado pior, pico e período crítico do perfil
     * mais carregado, capacidade disponível e carga no pico somadas pelos perfis, e perfis fora dos
     * limites (estado diferente de OK).
     */
    public PortfolioCapacity summarize(Collection<DemandProfileRequirement> requirements) {
        BigDecimal totalAvailable = BigDecimal.ZERO;
        BigDecimal totalLoad = BigDecimal.ZERO;
        BigDecimal peakUtilization = null;
        YearMonth criticalPeriod = null;
        CapacityStatus overall = CapacityStatus.OK;
        int outOfBounds = 0;
        List<ProfileUtilization> profiles = new ArrayList<>(profileUtilizations(requirements).values());
        for (ProfileUtilization util : profiles) {
            if (util.availableCapacity() != null && util.availableCapacity() > 0) {
                totalAvailable = totalAvailable.add(BigDecimal.valueOf(util.availableCapacity()));
            }
            if (util.requiredLoadAtPeak() != null) {
                totalLoad = totalLoad.add(util.requiredLoadAtPeak());
            }
            if (util.peakUtilization() != null
                    && (peakUtilization == null || util.peakUtilization().compareTo(peakUtilization) > 0)) {
                peakUtilization = util.peakUtilization();
                criticalPeriod = util.criticalPeriod();
            }
            overall = CapacityStatus.worst(overall, util.status());
            if (util.status() != CapacityStatus.OK) {
                outOfBounds++;
            }
        }
        profiles.sort(Comparator
                .comparingInt((ProfileUtilization p) -> -p.status().severity())
                .thenComparing(p -> p.peakUtilization() == null ? BigDecimal.ZERO : p.peakUtilization(),
                        Comparator.reverseOrder()));
        return new PortfolioCapacity(overall, peakUtilization, criticalPeriod,
                totalAvailable.setScale(2, RoundingMode.HALF_UP), totalLoad.setScale(2, RoundingMode.HALF_UP),
                outOfBounds, List.copyOf(profiles));
    }

    /** Estado agregado (pior) de uma Demanda dado o estado dos perfis que requer. */
    public CapacityStatus demandStatus(Collection<UUID> requiredProfileIds,
            Map<UUID, ProfileUtilization> utilizations) {
        if (requiredProfileIds == null || requiredProfileIds.isEmpty()) {
            return CapacityStatus.OK;
        }
        CapacityStatus worst = null;
        for (UUID profileId : requiredProfileIds) {
            ProfileUtilization util = utilizations.get(profileId);
            worst = CapacityStatus.worst(worst, util == null ? CapacityStatus.NOT_CONFIGURED : util.status());
        }
        return worst == null ? CapacityStatus.OK : worst;
    }

    /** Meses ocupados por uma entrada de requisito (janela das suas fases, ou da Demanda sem fases). */
    public Set<YearMonth> requirementMonths(DemandProfileRequirement requirement) {
        return occupiedMonths(requirement, new HashMap<>());
    }

    /**
     * Recorta uma {@link ProfileUtilization} global aos meses indicados, recalculando pico, período
     * crítico e estado só nesse recorte. Usado para avaliar cada entrada apenas nas suas fases.
     */
    public ProfileUtilization scopeToMonths(ProfileUtilization full, Set<YearMonth> months) {
        if (full == null || full.status() == CapacityStatus.NOT_CONFIGURED) {
            return full;
        }
        List<PeriodUtilization> scoped = full.periods().stream()
                .filter(p -> months.contains(p.period()))
                .toList();
        if (scoped.isEmpty()) {
            return new ProfileUtilization(
                    full.profileId(), full.profileName(), full.directionCode(), full.active(),
                    full.availableCapacity(), full.simultaneousDemandCapacity(),
                    CapacityStatus.OK, null, null, null, 0,
                    full.unscheduledDemands(), full.unscheduledLoad(), List.of());
        }
        PeriodUtilization peak = scoped.get(0);
        for (PeriodUtilization p : scoped) {
            if (p.utilization().compareTo(peak.utilization()) > 0) {
                peak = p;
            }
        }
        return new ProfileUtilization(
                full.profileId(), full.profileName(), full.directionCode(), full.active(),
                full.availableCapacity(), full.simultaneousDemandCapacity(),
                thresholds.classify(peak.utilization()), peak.utilization(), peak.period(),
                peak.requiredLoad(), peak.simultaneousDemands(),
                full.unscheduledDemands(), full.unscheduledLoad(), scoped);
    }

    private ProfileUtilization computeForProfile(Profile profile, List<DemandProfileRequirement> reqs) {
        Integer available = profile.getAvailableCapacity();
        Integer simultaneousCap = profile.getSimultaneousDemandCapacity();
        boolean notConfigured = available == null || available <= 0
                || simultaneousCap == null || simultaneousCap <= 0;

        int unscheduled = 0;
        BigDecimal unscheduledLoad = BigDecimal.ZERO;
        Map<YearMonth, Bucket> buckets = new TreeMap<>();
        Map<UUID, List<DemandProfileRequirement>> reqsByDemand = reqs.stream()
                .collect(Collectors.groupingBy(r -> r.getDemand().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Map<UUID, YearMonth[]>> windowCache = new HashMap<>();
        for (List<DemandProfileRequirement> demandReqs : reqsByDemand.values()) {
            Demand demand = demandReqs.get(0).getDemand();
            LocalDate start = demand.getPlannedStartDate();
            LocalDate end = demand.getDesiredDate();
            BigDecimal demandLoad = demandReqs.stream()
                    .map(DemandProfileRequirement::getEquivalentLoad)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (start == null || end == null || end.isBefore(start)) {
                unscheduled++;
                unscheduledLoad = unscheduledLoad.add(demandLoad);
                continue;
            }
            // Agrega a carga por mês da Demanda antes de contar: cada Demanda conta uma vez por mês.
            Map<YearMonth, BigDecimal> monthLoad = new TreeMap<>();
            for (DemandProfileRequirement req : demandReqs) {
                BigDecimal load = req.getEquivalentLoad();
                for (YearMonth m : occupiedMonths(req, windowCache)) {
                    monthLoad.merge(m, load, BigDecimal::add);
                }
            }
            for (Map.Entry<YearMonth, BigDecimal> entry : monthLoad.entrySet()) {
                buckets.computeIfAbsent(entry.getKey(), k -> new Bucket()).add(entry.getValue());
            }
        }

        if (notConfigured) {
            return new ProfileUtilization(
                    profile.getId(), profile.getName(), profile.getDirectionCode(), profile.isActive(),
                    available, simultaneousCap,
                    CapacityStatus.NOT_CONFIGURED, null, null,
                    null, 0, unscheduled, unscheduledLoad, List.of());
        }

        BigDecimal availableDec = BigDecimal.valueOf(available);
        BigDecimal simultaneousCapDec = BigDecimal.valueOf(simultaneousCap);
        List<PeriodUtilization> periods = new ArrayList<>(buckets.size());
        YearMonth peakPeriod = null;
        BigDecimal peakUtilization = null;
        BigDecimal peakLoad = BigDecimal.ZERO;
        int peakSimultaneous = 0;
        for (Map.Entry<YearMonth, Bucket> entry : buckets.entrySet()) {
            Bucket bucket = entry.getValue();
            BigDecimal peopleUtil = percentage(bucket.load, availableDec);
            BigDecimal simultUtil = percentage(BigDecimal.valueOf(bucket.count), simultaneousCapDec);
            // O estado é governado apenas pela ocupação de demandas simultâneas; a razão de
            // pessoas (requiredQuantity ÷ availableCapacity) fica como informação, não classifica.
            BigDecimal util = simultUtil;
            CapacityStatus status = thresholds.classify(util);
            periods.add(new PeriodUtilization(entry.getKey(),
                    bucket.load.setScale(2, RoundingMode.HALF_UP), bucket.count,
                    peopleUtil, simultUtil, util, status));
            if (peakUtilization == null || util.compareTo(peakUtilization) > 0) {
                peakUtilization = util;
                peakPeriod = entry.getKey();
                peakLoad = bucket.load;
                peakSimultaneous = bucket.count;
            }
        }
        CapacityStatus profileStatus = peakUtilization == null
                ? CapacityStatus.OK
                : thresholds.classify(peakUtilization);
        return new ProfileUtilization(
                profile.getId(), profile.getName(), profile.getDirectionCode(), profile.isActive(),
                available, simultaneousCap,
                profileStatus, peakUtilization, peakPeriod,
                peakUtilization == null ? null : peakLoad.setScale(2, RoundingMode.HALF_UP),
                peakSimultaneous, unscheduled, unscheduledLoad,
                Collections.unmodifiableList(periods));
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /** Meses ocupados por um requisito: janela das suas fases ou, sem fases, a janela total da Demanda. */
    private Set<YearMonth> occupiedMonths(DemandProfileRequirement req,
            Map<UUID, Map<UUID, YearMonth[]>> windowCache) {
        Demand demand = req.getDemand();
        LocalDate start = demand.getPlannedStartDate();
        LocalDate end = demand.getDesiredDate();
        if (start == null || end == null || end.isBefore(start)) {
            return Set.of();
        }
        Set<PhaseDefinition> phases = req.getPhases();
        if (phases == null || phases.isEmpty()) {
            return monthRange(YearMonth.from(start), YearMonth.from(end));
        }
        Map<UUID, YearMonth[]> windows =
                windowCache.computeIfAbsent(demand.getId(), id -> phaseWindows(demand));
        Set<YearMonth> months = new TreeSet<>();
        for (PhaseDefinition phase : phases) {
            YearMonth[] window = windows.get(phase.getId());
            if (window != null) {
                months.addAll(monthRange(window[0], window[1]));
            }
        }
        return months.isEmpty() ? monthRange(YearMonth.from(start), YearMonth.from(end)) : months;
    }

    /**
     * Janela {@code [mêsInício, mêsFim]} de cada fase do {@code PhaseModel} da Demanda, obtida
     * fatiando a janela total pelas percentagens acumuladas (normalizadas, caso não somem 100%).
     */
    private Map<UUID, YearMonth[]> phaseWindows(Demand demand) {
        LocalDate start = demand.getPlannedStartDate();
        LocalDate end = demand.getDesiredDate();
        List<PhaseSlice> phases = phaseSlices(demand);
        if (phases.isEmpty() || start == null || end == null) {
            return Map.of();
        }
        BigDecimal totalPercent = phases.stream()
                .map(PhaseSlice::weight)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPercent.signum() <= 0) {
            return Map.of();
        }
        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        BigDecimal totalDaysDec = BigDecimal.valueOf(totalDays);
        List<PhaseSlice> ordered = phases.stream()
                .sorted(Comparator.comparing(PhaseSlice::sequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<UUID, YearMonth[]> windows = new LinkedHashMap<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (PhaseSlice phase : ordered) {
            BigDecimal weight = phase.weight() == null ? BigDecimal.ZERO : phase.weight();
            BigDecimal startFraction = cumulative.divide(totalPercent, 10, RoundingMode.HALF_UP);
            cumulative = cumulative.add(weight);
            BigDecimal endFraction = cumulative.divide(totalPercent, 10, RoundingMode.HALF_UP);
            long startOffset = startFraction.multiply(totalDaysDec).setScale(0, RoundingMode.FLOOR).longValue();
            long endOffset = endFraction.multiply(totalDaysDec).setScale(0, RoundingMode.CEILING).longValue() - 1;
            startOffset = Math.max(0, Math.min(startOffset, totalDays - 1));
            endOffset = Math.max(startOffset, Math.min(endOffset, totalDays - 1));
            YearMonth from = YearMonth.from(start.plusDays(startOffset));
            YearMonth to = YearMonth.from(start.plusDays(endOffset));
            windows.put(phase.id(), new YearMonth[] { from, to });
        }
        return windows;
    }

    /**
     * Fases efetivas para o cálculo das janelas: as do planeamento personalizado (apenas ativas)
     * quando presente; caso contrário, as fases do Tipo da Demanda.
     */
    private List<PhaseSlice> phaseSlices(Demand demand) {
        if (demand.getCustomPhases() != null && !demand.getCustomPhases().isEmpty()) {
            return demand.getCustomPhases().stream()
                    .filter(DemandCustomPhase::isActive)
                    .map(p -> new PhaseSlice(p.getId(), p.getSequence(), p.getDurationWeightPercentage()))
                    .toList();
        }
        List<PhaseDefinition> phases = demand.getType() == null ? List.of() : demand.getType().getPhases();
        return phases.stream()
                .map(p -> new PhaseSlice(p.getId(), p.getSequence(), p.getDurationWeightPercentage()))
                .toList();
    }

    private record PhaseSlice(UUID id, Integer sequence, BigDecimal weight) {
    }

    private Set<YearMonth> monthRange(YearMonth from, YearMonth to) {
        Set<YearMonth> months = new TreeSet<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    private static final class Bucket {
        BigDecimal load = BigDecimal.ZERO;
        int count = 0;

        void add(BigDecimal load) {
            this.load = this.load.add(load);
            this.count++;
        }
    }
}
