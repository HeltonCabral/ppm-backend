package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ApplyCriteriaRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ApplyCriteriaResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CriterionRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CriterionResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.DuplicateRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ExcludeRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.FinalizeRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.FinalizeResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.IncludeRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ReorderRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ReorderResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.Response;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioRankInfo;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.UpdateRequest;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.OperationalPlan;
import cvt.cv.ppmbackend.entity.PlanningScenario;
import cvt.cv.ppmbackend.entity.PlanningScenarioCriterion;
import cvt.cv.ppmbackend.entity.PlanningScenarioItem;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.InclusionSource;
import cvt.cv.ppmbackend.enums.ScenarioCriterionType;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioCriterionRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioItemRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Composição de cenários de portfólio. Enquanto o cenário está em DRAFT nada é escrito no
 * DemandPlanningCycle — só a operação de finalização decide o destino das Demandas.
 */
@Service
@Transactional
public class PlanningScenarioService {

    private final PlanningScenarioRepository scenarios;
    private final PlanningScenarioItemRepository items;
    private final PlanningScenarioCriterionRepository criteria;
    private final StrategicPlanRepository strategicPlans;
    private final OperationalPlanRepository operationalPlans;
    private final DemandPlanningCycleRepository planningCycles;
    private final ScenarioPoolService pool;
    private final ScenarioCriteriaEvaluator evaluator;
    private final DemandHistoryService historyService;

    public PlanningScenarioService(PlanningScenarioRepository scenarios, PlanningScenarioItemRepository items,
            PlanningScenarioCriterionRepository criteria, StrategicPlanRepository strategicPlans,
            OperationalPlanRepository operationalPlans, DemandPlanningCycleRepository planningCycles,
            ScenarioPoolService pool, ScenarioCriteriaEvaluator evaluator, DemandHistoryService historyService) {
        this.scenarios = scenarios;
        this.items = items;
        this.criteria = criteria;
        this.strategicPlans = strategicPlans;
        this.operationalPlans = operationalPlans;
        this.planningCycles = planningCycles;
        this.pool = pool;
        this.evaluator = evaluator;
        this.historyService = historyService;
    }

    // ------------------------------------------------------------------- CRUD

    public Response create(CreateRequest request, String actor) {
        StrategicPlan plan = strategicPlans.findById(request.strategicPlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plano estratégico não encontrado: " + request.strategicPlanId()));
        requireUniqueName(plan.getId(), request.name(), null);

        PlanningScenario scenario = new PlanningScenario();
        scenario.setStrategicPlan(plan);
        scenario.setOperationalPlan(resolveOperationalPlan(request.operationalPlanId(), plan));
        scenario.setName(request.name().trim());
        scenario.setDescription(request.description());
        scenario.setStatus(ScenarioStatus.DRAFT);
        scenario.setCreatedBy(actor);
        return map(scenarios.save(scenario));
    }

    public Response update(UUID scenarioId, UpdateRequest request) {
        PlanningScenario scenario = requireDraft(scenarioId);
        requireUniqueName(scenario.getStrategicPlan().getId(), request.name(), scenarioId);
        scenario.setName(request.name().trim());
        scenario.setDescription(request.description());
        return map(scenarios.save(scenario));
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID strategicPlanId, ScenarioStatus status) {
        List<PlanningScenario> found = status == null
                ? scenarios.findByStrategicPlan_IdOrderByCreatedAtDesc(strategicPlanId)
                : scenarios.findByStrategicPlan_IdAndStatusOrderByCreatedAtDesc(strategicPlanId, status);
        return found.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID scenarioId) {
        return map(requireScenario(scenarioId));
    }

    public Response duplicate(UUID scenarioId, DuplicateRequest request, String actor) {
        PlanningScenario source = requireScenario(scenarioId);
        requireUniqueName(source.getStrategicPlan().getId(), request.name(), null);

        PlanningScenario copy = new PlanningScenario();
        copy.setStrategicPlan(source.getStrategicPlan());
        copy.setOperationalPlan(source.getOperationalPlan());
        copy.setName(request.name().trim());
        copy.setDescription(request.description() != null ? request.description() : source.getDescription());
        copy.setStatus(ScenarioStatus.DRAFT);
        copy.setCreatedBy(actor);
        PlanningScenario saved = scenarios.save(copy);

        if (!Boolean.FALSE.equals(request.copyCriteria())) {
            for (PlanningScenarioCriterion criterion : criteria.findByScenario_IdOrderByDisplayOrderAsc(scenarioId)) {
                PlanningScenarioCriterion copied = new PlanningScenarioCriterion();
                copied.setScenario(saved);
                copied.setCriterionCode(criterion.getCriterionCode());
                copied.setOperator(criterion.getOperator());
                copied.setValue(criterion.getValue());
                copied.setCriterionType(criterion.getCriterionType());
                copied.setDisplayOrder(criterion.getDisplayOrder());
                criteria.save(copied);
            }
        }
        if (!Boolean.FALSE.equals(request.copyItems())) {
            for (PlanningScenarioItem item : items.findByScenario_Id(scenarioId)) {
                PlanningScenarioItem copied = new PlanningScenarioItem();
                copied.setScenario(saved);
                copied.setDemandPlanningCycle(item.getDemandPlanningCycle());
                copied.setIncluded(item.isIncluded());
                copied.setScenarioRank(item.getScenarioRank());
                copied.setInclusionSource(item.getInclusionSource());
                copied.setComment(item.getComment());
                items.save(copied);
            }
        }
        return map(saved);
    }

    public void archive(UUID scenarioId) {
        PlanningScenario scenario = requireScenario(scenarioId);
        if (scenario.getStatus() == ScenarioStatus.FINAL) {
            throw new DomainException(HttpStatus.CONFLICT, "FINAL_SCENARIO_CANNOT_BE_ARCHIVED",
                    "O cenário final não pode ser arquivado", Map.of());
        }
        scenario.setStatus(ScenarioStatus.ARCHIVED);
        scenarios.save(scenario);
    }

    // -------------------------------------------------------------- critérios

    @Transactional(readOnly = true)
    public List<CriterionResponse> listCriteria(UUID scenarioId) {
        requireScenario(scenarioId);
        return criteria.findByScenario_IdOrderByDisplayOrderAsc(scenarioId).stream().map(this::map).toList();
    }

    public CriterionResponse addCriterion(UUID scenarioId, CriterionRequest request) {
        PlanningScenario scenario = requireDraft(scenarioId);
        evaluator.validate(request.criterionCode(), request.operator(), request.value());

        PlanningScenarioCriterion criterion = new PlanningScenarioCriterion();
        criterion.setScenario(scenario);
        criterion.setCriterionCode(request.criterionCode());
        criterion.setOperator(request.operator());
        criterion.setValue(request.value().trim());
        criterion.setCriterionType(
                request.criterionType() == null ? ScenarioCriterionType.FILTER : request.criterionType());
        criterion.setDisplayOrder(request.displayOrder() != null ? request.displayOrder()
                : (int) criteria.countByScenario_Id(scenarioId));
        return map(criteria.save(criterion));
    }

    public CriterionResponse updateCriterion(UUID scenarioId, UUID criterionId, CriterionRequest request) {
        requireDraft(scenarioId);
        PlanningScenarioCriterion criterion = criteria.findByIdAndScenario_Id(criterionId, scenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Critério não encontrado: " + criterionId));
        evaluator.validate(request.criterionCode(), request.operator(), request.value());

        criterion.setCriterionCode(request.criterionCode());
        criterion.setOperator(request.operator());
        criterion.setValue(request.value().trim());
        if (request.criterionType() != null) {
            criterion.setCriterionType(request.criterionType());
        }
        if (request.displayOrder() != null) {
            criterion.setDisplayOrder(request.displayOrder());
        }
        return map(criteria.save(criterion));
    }

    public void deleteCriterion(UUID scenarioId, UUID criterionId) {
        requireDraft(scenarioId);
        PlanningScenarioCriterion criterion = criteria.findByIdAndScenario_Id(criterionId, scenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Critério não encontrado: " + criterionId));
        criteria.delete(criterion);
    }

    /**
     * Gera a proposta de composição a partir dos critérios. Ajustes manuais anteriores são
     * preservados por omissão — o Comité continua a mandar sobre a proposta automática.
     */
    public ApplyCriteriaResponse applyCriteria(UUID scenarioId, ApplyCriteriaRequest request) {
        PlanningScenario scenario = requireDraft(scenarioId);
        List<PlanningScenarioCriterion> scenarioCriteria = criteria.findByScenario_IdOrderByDisplayOrderAsc(scenarioId);
        if (scenarioCriteria.isEmpty()) {
            throw new BadRequestException("O cenário não tem critérios definidos");
        }
        boolean reset = Boolean.TRUE.equals(request == null ? null : request.resetManualAdjustments());

        List<ScenarioDemandSnapshot> eligible = pool.eligiblePool(scenario.getStrategicPlan().getId());
        Map<UUID, PlanningScenarioItem> existing = new HashMap<>();
        items.findByScenario_Id(scenarioId).forEach(item -> existing.put(item.getDemandPlanningCycle().getId(), item));

        int matched = 0;
        int manualInclusions = 0;
        int manualExclusions = 0;
        List<PlanningScenarioItem> toSave = new ArrayList<>();

        for (ScenarioDemandSnapshot snapshot : eligible) {
            boolean matches = evaluator.matches(snapshot, scenarioCriteria);
            if (matches) {
                matched++;
            }
            PlanningScenarioItem item = existing.get(snapshot.cycleId());
            if (item == null) {
                item = new PlanningScenarioItem();
                item.setScenario(scenario);
                item.setDemandPlanningCycle(snapshot.cycle());
            } else if (!reset && item.getInclusionSource() == InclusionSource.MANUAL) {
                // Decisão manual prevalece sobre os critérios.
                if (item.isIncluded()) {
                    manualInclusions++;
                } else {
                    manualExclusions++;
                }
                continue;
            }
            item.setIncluded(matches);
            item.setInclusionSource(InclusionSource.CRITERIA);
            toSave.add(item);
        }

        // Demandas que saíram do pool elegível deixam de fazer parte do cenário.
        List<UUID> eligibleCycleIds = eligible.stream().map(ScenarioDemandSnapshot::cycleId).toList();
        List<PlanningScenarioItem> orphans = existing.values().stream()
                .filter(item -> !eligibleCycleIds.contains(item.getDemandPlanningCycle().getId()))
                .toList();
        if (!orphans.isEmpty()) {
            items.deleteAll(orphans);
        }

        items.saveAll(toSave);
        renumber(scenarioId);
        scenarios.save(scenario);

        List<PlanningScenarioItem> finalItems = items.findByScenario_Id(scenarioId);
        int included = (int) finalItems.stream().filter(PlanningScenarioItem::isIncluded).count();
        return new ApplyCriteriaResponse(scenarioId, eligible.size(), matched, included,
                finalItems.size() - included, manualInclusions, manualExclusions,
                scenarioCriteria.stream().map(this::map).toList());
    }

    // ------------------------------------------------------------------ itens

    public ApplyCriteriaResponse include(UUID scenarioId, IncludeRequest request) {
        return applyManualDecision(scenarioId, request.demandIds(), true, request.comment());
    }

    public ApplyCriteriaResponse exclude(UUID scenarioId, ExcludeRequest request) {
        return applyManualDecision(scenarioId, request.demandIds(), false, request.comment());
    }

    private ApplyCriteriaResponse applyManualDecision(UUID scenarioId, List<UUID> demandIds, boolean included,
            String comment) {
        PlanningScenario scenario = requireDraft(scenarioId);
        UUID planId = scenario.getStrategicPlan().getId();

        for (UUID demandId : demandIds) {
            DemandPlanningCycle cycle = planningCycles.findByDemand_IdAndStrategicPlan_Id(demandId, planId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Demanda sem ciclo de planeamento neste plano: " + demandId));
            if (included && cycle.getStatus() != DemandPlanningStatus.PRIORITIZED) {
                throw new DomainException(HttpStatus.CONFLICT, "DEMAND_NOT_ELIGIBLE_FOR_SCENARIO",
                        "Apenas Demandas priorizadas podem integrar um cenário",
                        Map.of("demandId", demandId.toString(), "status", cycle.getStatus().name()));
            }
            PlanningScenarioItem item = items.findByScenario_IdAndDemandPlanningCycle_Id(scenarioId, cycle.getId())
                    .orElseGet(() -> {
                        PlanningScenarioItem created = new PlanningScenarioItem();
                        created.setScenario(scenario);
                        created.setDemandPlanningCycle(cycle);
                        return created;
                    });
            item.setIncluded(included);
            item.setInclusionSource(InclusionSource.MANUAL);
            if (comment != null && !comment.isBlank()) {
                item.setComment(comment.trim());
            }
            items.save(item);
        }
        renumber(scenarioId);

        List<PlanningScenarioItem> all = items.findByScenario_Id(scenarioId);
        int includedCount = (int) all.stream().filter(PlanningScenarioItem::isIncluded).count();
        return new ApplyCriteriaResponse(scenarioId, all.size(), 0, includedCount, all.size() - includedCount,
                0, 0, listCriteria(scenarioId));
    }

    /** Move uma Demanda incluída para outra posição, renumerando as restantes de forma contígua. */
    public ReorderResponse reorder(UUID scenarioId, ReorderRequest request) {
        PlanningScenario scenario = requireDraft(scenarioId);
        DemandPlanningCycle cycle = planningCycles
                .findByDemand_IdAndStrategicPlan_Id(request.demandId(), scenario.getStrategicPlan().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demanda sem ciclo de planeamento neste plano: " + request.demandId()));
        PlanningScenarioItem target = items.findByScenario_IdAndDemandPlanningCycle_Id(scenarioId, cycle.getId())
                .filter(PlanningScenarioItem::isIncluded)
                .orElseThrow(() -> new BadRequestException("A Demanda não está incluída neste cenário"));

        List<PlanningScenarioItem> included = new ArrayList<>(
                items.findByScenario_IdAndIncludedTrueOrderByScenarioRankAsc(scenarioId));
        included.sort(rankComparator());

        int newPosition = request.newPosition();
        if (newPosition < 1 || newPosition > included.size()) {
            throw new BadRequestException("newPosition deve estar entre 1 e " + included.size());
        }
        Map<UUID, Integer> previousRanks = new LinkedHashMap<>();
        included.forEach(item -> previousRanks.put(item.getId(), item.getScenarioRank()));
        Integer previousRank = target.getScenarioRank();

        included.removeIf(item -> item.getId().equals(target.getId()));
        included.add(newPosition - 1, target);
        for (int i = 0; i < included.size(); i++) {
            included.get(i).setScenarioRank(i + 1);
        }
        items.saveAll(included);

        List<ScenarioRankInfo> affected = included.stream()
                .filter(item -> !item.getId().equals(target.getId()))
                .filter(item -> !java.util.Objects.equals(previousRanks.get(item.getId()), item.getScenarioRank()))
                .map(item -> {
                    Demand demand = item.getDemandPlanningCycle().getDemand();
                    return new ScenarioRankInfo(demand.getId(), demand.getCode(), demand.getTitle(),
                            previousRanks.get(item.getId()), item.getScenarioRank());
                })
                .toList();
        return new ReorderResponse(scenarioId, request.demandId(), previousRank, target.getScenarioRank(), affected);
    }

    // -------------------------------------------------------------- finalizar

    /**
     * Seleciona o cenário como FINAL: incluídas passam a IN_PLAN com o scenarioRank como
     * portfolioRank, excluídas passam a BACKLOG. Operação única e transacional por ciclo.
     */
    public FinalizeResponse finalizeScenario(UUID scenarioId, FinalizeRequest request, String actorId,
            String actorName) {
        PlanningScenario scenario = requireScenario(scenarioId);
        if (scenario.getStatus() == ScenarioStatus.FINAL) {
            throw new DomainException(HttpStatus.CONFLICT, "SCENARIO_ALREADY_FINAL",
                    "O cenário já é o cenário final", Map.of());
        }
        if (scenario.getStatus() == ScenarioStatus.ARCHIVED) {
            throw new DomainException(HttpStatus.CONFLICT, "ARCHIVED_SCENARIO_CANNOT_BE_FINAL",
                    "Um cenário arquivado não pode ser selecionado", Map.of());
        }
        UUID planId = scenario.getStrategicPlan().getId();
        scenarios.findFirstByStrategicPlan_IdAndStatus(planId, ScenarioStatus.FINAL).ifPresent(existing -> {
            throw new DomainException(HttpStatus.CONFLICT, "FINAL_SCENARIO_ALREADY_EXISTS",
                    "Já existe um cenário final neste ciclo de planeamento",
                    Map.of("scenarioId", existing.getId().toString(), "name", existing.getName()));
        });

        List<PlanningScenarioItem> allItems = items.findByScenario_Id(scenarioId);
        if (allItems.stream().noneMatch(PlanningScenarioItem::isIncluded)) {
            throw new BadRequestException("O cenário não tem nenhuma Demanda incluída");
        }
        List<PlanningScenarioItem> underReview = allItems.stream()
                .filter(PlanningScenarioItem::isIncluded)
                .filter(item -> item.getDemandPlanningCycle().getStatus() == DemandPlanningStatus.IN_REVIEW)
                .toList();
        if (!underReview.isEmpty()) {
            throw new DomainException(HttpStatus.CONFLICT, "DEMANDS_UNDER_REVIEW_IN_SCENARIO",
                    "Existem Demandas em revisão incluídas no cenário. Conclua a revisão antes de finalizar.",
                    Map.of("demands", underReview.stream()
                            .map(item -> item.getDemandPlanningCycle().getDemand().getCode())
                            .toList()));
        }

        List<PlanningScenarioItem> included = allItems.stream()
                .filter(PlanningScenarioItem::isIncluded)
                .sorted(rankComparator())
                .toList();

        int rank = 1;
        List<DemandPlanningCycle> touched = new ArrayList<>();
        for (PlanningScenarioItem item : included) {
            DemandPlanningCycle cycle = item.getDemandPlanningCycle();
            DemandPlanningStatus previous = cycle.getStatus();
            item.setScenarioRank(rank);
            cycle.setPortfolioRank(rank);
            cycle.setStatus(DemandPlanningStatus.IN_PLAN);
            touched.add(cycle);
            logDecision(cycle, previous, DemandPlanningStatus.IN_PLAN, scenario, rank, actorId, actorName,
                    request == null ? null : request.justification());
            rank++;
        }

        // Todo o pool elegível que não foi incluído fica em backlog.
        List<ScenarioDemandSnapshot> eligible = pool.eligiblePool(planId);
        List<UUID> includedCycleIds = included.stream()
                .map(item -> item.getDemandPlanningCycle().getId())
                .toList();
        int backlogged = 0;
        for (ScenarioDemandSnapshot snapshot : eligible) {
            if (includedCycleIds.contains(snapshot.cycleId())) {
                continue;
            }
            DemandPlanningCycle cycle = snapshot.cycle();
            DemandPlanningStatus previous = cycle.getStatus();
            cycle.setRankBeforeBacklog(cycle.getPortfolioRank());
            cycle.setPortfolioRank(null);
            cycle.setStatus(DemandPlanningStatus.BACKLOG);
            touched.add(cycle);
            backlogged++;
            logDecision(cycle, previous, DemandPlanningStatus.BACKLOG, scenario, null, actorId, actorName,
                    request == null ? null : request.justification());
        }
        planningCycles.saveAll(touched);

        int archived = 0;
        for (PlanningScenario other : scenarios.findByStrategicPlan_IdOrderByCreatedAtDesc(planId)) {
            if (!other.getId().equals(scenarioId) && other.getStatus() == ScenarioStatus.DRAFT) {
                other.setStatus(ScenarioStatus.ARCHIVED);
                scenarios.save(other);
                archived++;
            }
        }

        items.saveAll(included);
        scenario.setStatus(ScenarioStatus.FINAL);
        scenario.setSelectedBy(actorName);
        scenario.setSelectedAt(Instant.now());
        scenarios.save(scenario);

        return new FinalizeResponse(scenarioId, ScenarioStatus.FINAL, included.size(), backlogged, archived,
                scenario.getSelectedBy(), scenario.getSelectedAt());
    }

    private void logDecision(DemandPlanningCycle cycle, DemandPlanningStatus from, DemandPlanningStatus to,
            PlanningScenario scenario, Integer rank, String actorId, String actorName, String justification) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scenarioId", scenario.getId().toString());
        metadata.put("scenarioName", scenario.getName());
        if (rank != null) {
            metadata.put("portfolioRank", String.valueOf(rank));
        }
        historyService.log(cycle.getDemand(), "SCENARIO_DECISION_" + to.name(), from.name(), to.name(),
                justification != null && !justification.isBlank() ? justification
                        : "Decisão resultante da seleção do cenário " + scenario.getName(),
                actorId, actorName, metadata);
    }

    // ------------------------------------------------------------------ utils

    /** Renumera de forma contígua (1..n) as Demandas incluídas, preservando a ordem relativa. */
    private void renumber(UUID scenarioId) {
        List<PlanningScenarioItem> included = new ArrayList<>(
                items.findByScenario_IdAndIncludedTrueOrderByScenarioRankAsc(scenarioId));
        included.sort(rankComparator());
        int rank = 1;
        for (PlanningScenarioItem item : included) {
            item.setScenarioRank(rank++);
        }
        items.saveAll(included);

        List<PlanningScenarioItem> excluded = items.findByScenario_Id(scenarioId).stream()
                .filter(item -> !item.isIncluded())
                .filter(item -> item.getScenarioRank() != null)
                .toList();
        excluded.forEach(item -> item.setScenarioRank(null));
        items.saveAll(excluded);
    }

    /**
     * Itens ainda sem scenarioRank entram pela ordem do portfólio, para que a proposta inicial
     * respeite a priorização oficial.
     */
    private Comparator<PlanningScenarioItem> rankComparator() {
        return Comparator
                .comparing((PlanningScenarioItem item) -> item.getScenarioRank() == null ? Integer.MAX_VALUE
                        : item.getScenarioRank())
                .thenComparing(item -> item.getDemandPlanningCycle().getPortfolioRank() == null ? Integer.MAX_VALUE
                        : item.getDemandPlanningCycle().getPortfolioRank())
                .thenComparing(item -> item.getDemandPlanningCycle().getDemand().getCode(),
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    @Transactional(readOnly = true)
    public PlanningScenario requireScenario(UUID scenarioId) {
        return scenarios.findById(scenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cenário não encontrado: " + scenarioId));
    }

    private PlanningScenario requireDraft(UUID scenarioId) {
        PlanningScenario scenario = requireScenario(scenarioId);
        if (scenario.getStatus() != ScenarioStatus.DRAFT) {
            throw new DomainException(HttpStatus.CONFLICT, "SCENARIO_NOT_EDITABLE",
                    "Apenas cenários em DRAFT podem ser alterados",
                    Map.of("status", scenario.getStatus().name()));
        }
        return scenario;
    }

    private void requireUniqueName(UUID strategicPlanId, String name, UUID currentId) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name é obrigatório");
        }
        boolean duplicated = scenarios.findByStrategicPlan_IdOrderByCreatedAtDesc(strategicPlanId).stream()
                .filter(scenario -> currentId == null || !scenario.getId().equals(currentId))
                .anyMatch(scenario -> scenario.getName().equalsIgnoreCase(name.trim()));
        if (duplicated) {
            throw new BadRequestException("Já existe um cenário com o nome " + name.trim() + " neste plano");
        }
    }

    private OperationalPlan resolveOperationalPlan(UUID operationalPlanId, StrategicPlan plan) {
        if (operationalPlanId != null) {
            return operationalPlans.findById(operationalPlanId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Plano operacional não encontrado: " + operationalPlanId));
        }
        return operationalPlans.findByStrategicPlan_Id(plan.getId()).stream()
                .min(Comparator.comparing(OperationalPlan::getFiscalYear,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    Response map(PlanningScenario scenario) {
        List<PlanningScenarioItem> scenarioItems = items.findByScenario_Id(scenario.getId());
        int included = (int) scenarioItems.stream().filter(PlanningScenarioItem::isIncluded).count();
        OperationalPlan operationalPlan = scenario.getOperationalPlan();
        return new Response(scenario.getId(), scenario.getStrategicPlan().getId(),
                scenario.getStrategicPlan().getName(),
                operationalPlan == null ? null : operationalPlan.getId(),
                operationalPlan == null ? null : operationalPlan.getName(),
                scenario.getName(), scenario.getDescription(), scenario.getStatus(),
                included, scenarioItems.size() - included,
                (int) criteria.countByScenario_Id(scenario.getId()),
                scenario.getCreatedBy(), scenario.getCreatedAt(), scenario.getUpdatedAt(),
                scenario.getSelectedBy(), scenario.getSelectedAt());
    }

    CriterionResponse map(PlanningScenarioCriterion criterion) {
        return new CriterionResponse(criterion.getId(), criterion.getCriterionCode(), criterion.getOperator(),
                criterion.getValue(), criterion.getCriterionType(), criterion.getDisplayOrder(),
                criterion.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Optional<PlanningScenario> findFinalScenario(UUID strategicPlanId) {
        return scenarios.findFirstByStrategicPlan_IdAndStatus(strategicPlanId, ScenarioStatus.FINAL);
    }
}
