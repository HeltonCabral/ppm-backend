package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CapacityDtos.ProfileUtilization;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.AnalysisResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CapacityAnalysis;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CompareResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.DependencyAnalysis;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.Distributions;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.PortfolioMetrics;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ProfileCapacityInfo;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioComparisonEntry;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioDemandPage;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioDemandRow;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioExclusiveDemands;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.SituationCounters;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.Totals;
import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.PlanningScenario;
import cvt.cv.ppmbackend.entity.PlanningScenarioItem;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.CapacityStatus;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.DemandScenarioSituation;
import cvt.cv.ppmbackend.enums.InclusionSource;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioItemRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Leitura consolidada de um cenário: análise agregada, listagem paginada do pool e comparação
 * entre cenários. Todo o cálculo vive aqui — o frontend apenas apresenta.
 */
@Service
public class PlanningScenarioAnalysisService {

    private final PlanningScenarioService scenarioService;
    private final PlanningScenarioItemRepository items;
    private final ScenarioPoolService pool;
    private final DemandProfileRequirementRepository profileRequirements;
    private final DemandDependencyRepository dependencies;
    private final DemandCapacityAnalysisService capacityAnalysis;
    private final CapacityCalculator capacityCalculator;
    private final StrategicPlanRepository strategicPlans;

    public PlanningScenarioAnalysisService(PlanningScenarioService scenarioService,
            PlanningScenarioItemRepository items, ScenarioPoolService pool,
            DemandProfileRequirementRepository profileRequirements, DemandDependencyRepository dependencies,
            DemandCapacityAnalysisService capacityAnalysis, CapacityCalculator capacityCalculator,
            StrategicPlanRepository strategicPlans) {
        this.scenarioService = scenarioService;
        this.items = items;
        this.pool = pool;
        this.profileRequirements = profileRequirements;
        this.dependencies = dependencies;
        this.capacityAnalysis = capacityAnalysis;
        this.capacityCalculator = capacityCalculator;
        this.strategicPlans = strategicPlans;
    }

    // ---------------------------------------------------------------- análise

    @Transactional(readOnly = true)
    public AnalysisResponse analysis(UUID scenarioId) {
        PlanningScenario scenario = scenarioService.requireScenario(scenarioId);
        ScenarioView view = view(scenario);

        List<ScenarioDemandSnapshot> included = view.bySituation(DemandScenarioSituation.INCLUDED);
        List<ScenarioDemandSnapshot> inReview = view.bySituation(DemandScenarioSituation.IN_REVIEW);
        List<ScenarioDemandSnapshot> excluded = view.bySituation(DemandScenarioSituation.EXCLUDED);

        Totals totals = new Totals(view.eligibleCount(), included.size(), excluded.size(), inReview.size());

        BigDecimal scoreSum = included.stream()
                .map(ScenarioDemandSnapshot::score)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long scored = included.stream().filter(s -> s.score() != null).count();
        BigDecimal averageScore = scored == 0 ? null
                : scoreSum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP);
        BigDecimal estimatedCost = included.stream()
                .map(ScenarioDemandSnapshot::estimatedBudget)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        List<String> directions = included.stream()
                .map(ScenarioDemandSnapshot::directionLabel)
                .distinct()
                .sorted()
                .toList();
        int mandatoryIncluded = (int) included.stream().filter(ScenarioDemandSnapshot::mandatory).count();
        int mandatoryTotal = (int) view.all().stream().filter(ScenarioDemandSnapshot::mandatory).count();

        PortfolioMetrics metrics = new PortfolioMetrics(averageScore,
                scoreSum.setScale(2, RoundingMode.HALF_UP), estimatedCost,
                directions.size(), directions, mandatoryIncluded, mandatoryTotal);

        return new AnalysisResponse(
                scenarioService.map(scenario),
                scenarioService.listCriteria(scenarioId),
                totals,
                metrics,
                capacity(included),
                dependencyAnalysis(included, excluded),
                distributions(included));
    }

    /**
     * Análise ao nível do plano, sobre todas as demandas priorizadas, sem cenário associado.
     * Não persiste nada: reutiliza os agregados da análise de cenário tratando as PRIORITIZED como
     * incluídas e as IN_REVIEW como em revisão. {@code scenario} e {@code criteria} vêm vazios
     * porque não existe cenário.
     */
    @Transactional(readOnly = true)
    public AnalysisResponse planAnalysis(UUID strategicPlanId) {
        requirePlan(strategicPlanId);
        ScenarioView view = planView(strategicPlanId);

        List<ScenarioDemandSnapshot> included = view.bySituation(DemandScenarioSituation.INCLUDED);
        List<ScenarioDemandSnapshot> inReview = view.bySituation(DemandScenarioSituation.IN_REVIEW);

        Totals totals = new Totals(included.size() + inReview.size(), included.size(), 0, inReview.size());

        BigDecimal scoreSum = included.stream()
                .map(ScenarioDemandSnapshot::score)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long scored = included.stream().filter(s -> s.score() != null).count();
        BigDecimal averageScore = scored == 0 ? null
                : scoreSum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP);
        BigDecimal estimatedCost = included.stream()
                .map(ScenarioDemandSnapshot::estimatedBudget)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        List<String> directions = included.stream()
                .map(ScenarioDemandSnapshot::directionLabel)
                .distinct()
                .sorted()
                .toList();
        int mandatoryIncluded = (int) included.stream().filter(ScenarioDemandSnapshot::mandatory).count();
        int mandatoryTotal = (int) view.all().stream().filter(ScenarioDemandSnapshot::mandatory).count();

        PortfolioMetrics metrics = new PortfolioMetrics(averageScore,
                scoreSum.setScale(2, RoundingMode.HALF_UP), estimatedCost,
                directions.size(), directions, mandatoryIncluded, mandatoryTotal);

        return new AnalysisResponse(
                null,
                List.of(),
                totals,
                metrics,
                capacity(included),
                dependencyAnalysis(included, List.of()),
                distributions(included));
    }

    private CapacityAnalysis capacity(List<ScenarioDemandSnapshot> included) {
        Set<UUID> demandIds = included.stream().map(ScenarioDemandSnapshot::demandId).collect(Collectors.toSet());
        List<DemandProfileRequirement> requirements = demandIds.isEmpty()
                ? List.of()
                : profileRequirements.findByDemand_IdIn(demandIds);
        Map<UUID, ProfileUtilization> utilizations = capacityCalculator.profileUtilizations(requirements);

        BigDecimal totalAvailable = BigDecimal.ZERO;
        BigDecimal totalLoad = BigDecimal.ZERO;
        BigDecimal peakUtilization = null;
        YearMonth criticalPeriod = null;
        CapacityStatus overall = CapacityStatus.OK;
        int outOfBounds = 0;
        List<ProfileCapacityInfo> profileInfos = new ArrayList<>(utilizations.size());
        for (ProfileUtilization util : utilizations.values()) {
            profileInfos.add(new ProfileCapacityInfo(
                    util.profileId(), util.profileName(),
                    util.availableCapacity(), util.simultaneousDemandCapacity(),
                    util.status(), util.peakUtilization(), util.criticalPeriod(),
                    util.requiredLoadAtPeak(), util.simultaneousDemandsAtPeak(), util.periods()));
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
        profileInfos.sort(Comparator
                .comparingInt((ProfileCapacityInfo p) -> -p.status().severity())
                .thenComparing(p -> p.peakUtilization() == null ? BigDecimal.ZERO : p.peakUtilization(),
                        Comparator.reverseOrder()));
        return new CapacityAnalysis(
                overall, peakUtilization, criticalPeriod,
                totalAvailable.setScale(2, RoundingMode.HALF_UP),
                totalLoad.setScale(2, RoundingMode.HALF_UP),
                outOfBounds, List.copyOf(profileInfos));
    }

    private DependencyAnalysis dependencyAnalysis(List<ScenarioDemandSnapshot> included,
            List<ScenarioDemandSnapshot> excluded) {
        int total = included.stream().mapToInt(ScenarioDemandSnapshot::totalDependencies).sum();
        int blocking = included.stream().mapToInt(ScenarioDemandSnapshot::blockingDependencies).sum();
        int withBlocking = (int) included.stream().filter(s -> s.blockingDependencies() > 0).count();

        Set<UUID> includedDemandIds = included.stream().map(ScenarioDemandSnapshot::demandId)
                .collect(Collectors.toSet());
        Set<UUID> excludedDemandIds = excluded.stream().map(ScenarioDemandSnapshot::demandId)
                .collect(Collectors.toSet());
        int onExcluded = 0;
        if (!includedDemandIds.isEmpty()) {
            for (DemandDependency dependency : dependencies.findByDemand_IdIn(includedDemandIds)) {
                if (dependency.getType() == DemandDependencyType.BLOCKING
                        && excludedDemandIds.contains(dependency.getDependsOnDemand().getId())) {
                    onExcluded++;
                }
            }
        }
        return new DependencyAnalysis(total, blocking, withBlocking, onExcluded);
    }

    private Distributions distributions(List<ScenarioDemandSnapshot> included) {
        Map<String, Integer> byDirection = new LinkedHashMap<>();
        Map<String, Integer> byNature = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byDomain = new LinkedHashMap<>();
        for (ScenarioDemandSnapshot snapshot : included) {
            byDirection.merge(snapshot.directionLabel(), 1, Integer::sum);
            byNature.merge(snapshot.nature() == null ? "NÃO DEFINIDA" : snapshot.nature().name(), 1, Integer::sum);
            byType.merge(blankToLabel(snapshot.typeCode()), 1, Integer::sum);
            byDomain.merge(blankToLabel(snapshot.domainCode()), 1, Integer::sum);
        }
        return new Distributions(byDirection, byNature, byType, byDomain);
    }

    // -------------------------------------------------------- pool paginado

    @Transactional(readOnly = true)
    public ScenarioDemandPage demands(UUID scenarioId, ScenarioDemandFilters filters, int page, int size) {
        validatePaging(page, size);
        PlanningScenario scenario = scenarioService.requireScenario(scenarioId);
        return paginate(view(scenario), filters, page, size);
    }

    /**
     * Listagem do pool ao nível do plano, sobre as mesmas demandas contabilizadas em
     * {@link #planAnalysis(UUID)} (priorizadas + em revisão). Reutiliza o cálculo das linhas usado
     * na pool do cenário — não persiste nada.
     */
    @Transactional(readOnly = true)
    public ScenarioDemandPage planDemands(UUID strategicPlanId, ScenarioDemandFilters filters, int page, int size) {
        validatePaging(page, size);
        requirePlan(strategicPlanId);
        return paginate(planView(strategicPlanId), filters, page, size);
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page não pode ser negativo");
        }
        if (size < 1 || size > 200) {
            throw new BadRequestException("size deve estar entre 1 e 200");
        }
    }

    private ScenarioDemandPage paginate(ScenarioView view, ScenarioDemandFilters filters, int page, int size) {
        List<ScenarioDemandRow> rows = view.all().stream()
                .map(snapshot -> row(snapshot, view))
                .filter(filters::matches)
                .sorted(defaultOrder())
                .toList();

        SituationCounters counters = new SituationCounters(
                (int) rows.stream().filter(r -> r.situation() == DemandScenarioSituation.INCLUDED).count(),
                (int) rows.stream().filter(r -> r.situation() == DemandScenarioSituation.IN_REVIEW).count(),
                (int) rows.stream().filter(r -> r.situation() == DemandScenarioSituation.EXCLUDED).count(),
                rows.size());

        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        int totalPages = (int) Math.ceil(rows.size() / (double) size);
        return new ScenarioDemandPage(rows.subList(from, to), page, size, rows.size(), totalPages, counters);
    }

    /**
     * Ordenação obrigatória: INCLUDED, depois IN_REVIEW, depois EXCLUDED. Dentro de INCLUDED por
     * scenarioRank quando existe, senão por portfolioRank; nas restantes por portfolioRank.
     */
    private Comparator<ScenarioDemandRow> defaultOrder() {
        return Comparator
                .comparingInt((ScenarioDemandRow row) -> switch (row.situation()) {
                    case INCLUDED -> 0;
                    case IN_REVIEW -> 1;
                    case EXCLUDED -> 2;
                })
                .thenComparingInt(row -> {
                    Integer rank = row.situation() == DemandScenarioSituation.INCLUDED
                            ? (row.scenarioRank() != null ? row.scenarioRank() : row.portfolioRank())
                            : row.portfolioRank();
                    return rank == null ? Integer.MAX_VALUE : rank;
                })
                .thenComparing(ScenarioDemandRow::code, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private ScenarioDemandRow row(ScenarioDemandSnapshot snapshot, ScenarioView view) {
        PlanningScenarioItem item = view.itemOf(snapshot.cycleId());
        DemandScenarioSituation situation = view.situationOf(snapshot);
        return new ScenarioDemandRow(
                snapshot.demandId(),
                snapshot.cycleId(),
                snapshot.demand().getCode(),
                snapshot.demand().getTitle(),
                snapshot.demand().getRequester(),
                snapshot.directionCode(),
                snapshot.directionName(),
                snapshot.nature(),
                snapshot.typeCode(),
                snapshot.domainCode(),
                snapshot.pillarName(),
                snapshot.objectiveName(),
                snapshot.score(),
                snapshot.cycle().getScoreStatus(),
                snapshot.cycle().getPortfolioRank(),
                item == null ? null : item.getScenarioRank(),
                snapshot.complexity(),
                snapshot.demand().getComplexityScore(),
                view.capacityStatusOf(snapshot),
                snapshot.estimatedBudget(),
                snapshot.mandatoryDeadline(),
                snapshot.totalDependencies(),
                snapshot.blockingDependencies(),
                situation,
                item == null ? null : item.getInclusionSource(),
                item == null ? null : item.getComment());
    }

    // -------------------------------------------------------------- comparar

    @Transactional(readOnly = true)
    public CompareResponse compare(List<UUID> scenarioIds) {
        if (scenarioIds == null || scenarioIds.size() < 2) {
            throw new BadRequestException("Indique pelo menos dois cenários para comparar");
        }
        List<ScenarioComparisonEntry> entries = new ArrayList<>();
        Map<UUID, Set<UUID>> includedByScenario = new LinkedHashMap<>();

        for (UUID scenarioId : scenarioIds) {
            PlanningScenario scenario = scenarioService.requireScenario(scenarioId);
            ScenarioView view = view(scenario);
            List<ScenarioDemandSnapshot> included = view.bySituation(DemandScenarioSituation.INCLUDED);

            BigDecimal scoreSum = included.stream().map(ScenarioDemandSnapshot::score).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long scored = included.stream().filter(s -> s.score() != null).count();
            BigDecimal averageScore = scored == 0 ? null
                    : scoreSum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP);
            BigDecimal cost = included.stream().map(ScenarioDemandSnapshot::estimatedBudget)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            CapacityAnalysis includedCapacity = capacity(included);
            entries.add(new ScenarioComparisonEntry(scenarioId, scenario.getName(), scenario.getStatus(),
                    included.size(), averageScore, cost,
                    includedCapacity.overallStatus(), includedCapacity.peakUtilization(),
                    (int) included.stream().filter(ScenarioDemandSnapshot::mandatory).count(),
                    included.stream().mapToInt(ScenarioDemandSnapshot::blockingDependencies).sum(),
                    (int) included.stream().map(ScenarioDemandSnapshot::directionLabel).distinct().count()));

            includedByScenario.put(scenarioId,
                    included.stream().map(ScenarioDemandSnapshot::demandId).collect(Collectors.toCollection(HashSet::new)));
        }

        Set<UUID> common = null;
        for (Set<UUID> demandIds : includedByScenario.values()) {
            if (common == null) {
                common = new HashSet<>(demandIds);
            } else {
                common.retainAll(demandIds);
            }
        }
        Set<UUID> shared = common == null ? Set.of() : common;

        List<ScenarioExclusiveDemands> exclusive = includedByScenario.entrySet().stream()
                .map(entry -> {
                    Set<UUID> only = new HashSet<>(entry.getValue());
                    includedByScenario.forEach((otherId, otherDemands) -> {
                        if (!otherId.equals(entry.getKey())) {
                            only.removeAll(otherDemands);
                        }
                    });
                    return new ScenarioExclusiveDemands(entry.getKey(), List.copyOf(only));
                })
                .toList();

        return new CompareResponse(entries, List.copyOf(shared), exclusive);
    }

    // ------------------------------------------------------------------ utils

    private ScenarioView view(PlanningScenario scenario) {
        UUID planId = scenario.getStrategicPlan().getId();
        List<ScenarioDemandSnapshot> portfolio = pool.portfolioPool(planId);
        Map<UUID, PlanningScenarioItem> itemsByCycle = new HashMap<>();
        items.findByScenario_Id(scenario.getId())
                .forEach(item -> itemsByCycle.put(item.getDemandPlanningCycle().getId(), item));
        Map<UUID, CapacityStatus> capacityStatuses = capacityAnalysis.statusesForStrategicPlan(planId);
        return new ScenarioView(portfolio, itemsByCycle, capacityStatuses, false);
    }

    /**
     * Vista ao nível do plano: sem itens de cenário, restrita a PRIORITIZED e IN_REVIEW.
     * Sem cenário nada fica de fora, por isso {@link ScenarioView#situationOf} nunca devolve EXCLUDED.
     */
    private ScenarioView planView(UUID strategicPlanId) {
        List<ScenarioDemandSnapshot> portfolio = pool.portfolioPool(strategicPlanId).stream()
                .filter(snapshot -> {
                    DemandPlanningStatus status = snapshot.cycle().getStatus();
                    return status == DemandPlanningStatus.PRIORITIZED
                            || status == DemandPlanningStatus.IN_REVIEW;
                })
                .toList();
        Map<UUID, CapacityStatus> capacityStatuses = capacityAnalysis.statusesForStrategicPlan(strategicPlanId);
        return new ScenarioView(portfolio, Map.of(), capacityStatuses, true);
    }

    private StrategicPlan requirePlan(UUID strategicPlanId) {
        return strategicPlans.findById(strategicPlanId)
                .filter(plan -> plan.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plano estratégico não encontrado: " + strategicPlanId));
    }

    private static String blankToLabel(String value) {
        return value == null || value.isBlank() ? "NÃO DEFINIDO" : value.trim();
    }

    /**
     * Pool do portfólio combinado com os itens do cenário, para derivar a situação de cada Demanda.
     * Quando {@code planLevel} é {@code true} não há cenário — todas as Demandas que não estão em
     * revisão são consideradas incluídas por defeito.
     */
    private record ScenarioView(List<ScenarioDemandSnapshot> all, Map<UUID, PlanningScenarioItem> itemsByCycle,
            Map<UUID, CapacityStatus> capacityStatuses, boolean planLevel) {

        PlanningScenarioItem itemOf(UUID cycleId) {
            return itemsByCycle.get(cycleId);
        }

        String capacityStatusOf(ScenarioDemandSnapshot snapshot) {
            String persisted = snapshot.capacityStatus();
            if (persisted != null) {
                return persisted;
            }
            CapacityStatus computed = capacityStatuses.get(snapshot.demandId());
            return computed == null ? null : computed.name();
        }

        DemandScenarioSituation situationOf(ScenarioDemandSnapshot snapshot) {
            if (snapshot.cycle().getStatus() == DemandPlanningStatus.IN_REVIEW) {
                return DemandScenarioSituation.IN_REVIEW;
            }
            if (planLevel) {
                return DemandScenarioSituation.INCLUDED;
            }
            PlanningScenarioItem item = itemsByCycle.get(snapshot.cycleId());
            return item != null && item.isIncluded()
                    ? DemandScenarioSituation.INCLUDED
                    : DemandScenarioSituation.EXCLUDED;
        }

        List<ScenarioDemandSnapshot> bySituation(DemandScenarioSituation situation) {
            return all.stream().filter(snapshot -> situationOf(snapshot) == situation).toList();
        }

        int eligibleCount() {
            return (int) all.stream()
                    .filter(snapshot -> snapshot.cycle().getStatus() != DemandPlanningStatus.BACKLOG
                            || itemsByCycle.containsKey(snapshot.cycleId()))
                    .count();
        }
    }

    /** Filtros da listagem do pool. Todos opcionais e combinados por conjunção. */
    public record ScenarioDemandFilters(
            DemandScenarioSituation situation,
            String directionCode,
            DemandNature nature,
            String type,
            String domain,
            BigDecimal scoreMin,
            BigDecimal scoreMax,
            DemandComplexity complexity,
            String capacityStatus,
            LocalDate mandatoryDeadlineUntil,
            Integer blockingDependenciesMin,
            Integer blockingDependenciesMax,
            String search) {

        boolean matches(ScenarioDemandRow row) {
            if (situation != null && row.situation() != situation) {
                return false;
            }
            if (hasText(directionCode) && !equalsIgnoreCase(directionCode, row.directionCode())) {
                return false;
            }
            if (nature != null && row.nature() != nature) {
                return false;
            }
            if (hasText(type) && !equalsIgnoreCase(type, row.type())) {
                return false;
            }
            if (hasText(domain) && !equalsIgnoreCase(domain, row.domain())) {
                return false;
            }
            if (scoreMin != null && (row.score() == null || row.score().compareTo(scoreMin) < 0)) {
                return false;
            }
            if (scoreMax != null && (row.score() == null || row.score().compareTo(scoreMax) > 0)) {
                return false;
            }
            if (complexity != null && row.complexity() != complexity) {
                return false;
            }
            if (hasText(capacityStatus) && !equalsIgnoreCase(capacityStatus, row.capacityStatus())) {
                return false;
            }
            if (mandatoryDeadlineUntil != null
                    && (row.mandatoryDeadline() == null || row.mandatoryDeadline().isAfter(mandatoryDeadlineUntil))) {
                return false;
            }
            if (blockingDependenciesMin != null && row.blockingDependencies() < blockingDependenciesMin) {
                return false;
            }
            if (blockingDependenciesMax != null && row.blockingDependencies() > blockingDependenciesMax) {
                return false;
            }
            if (hasText(search)) {
                String needle = search.trim().toLowerCase(Locale.ROOT);
                boolean hit = contains(row.code(), needle) || contains(row.title(), needle)
                        || contains(row.requester(), needle);
                if (!hit) {
                    return false;
                }
            }
            return true;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        private static boolean equalsIgnoreCase(String expected, String actual) {
            return actual != null && actual.trim().equalsIgnoreCase(expected.trim());
        }

        private static boolean contains(String value, String needle) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
        }
    }
}
