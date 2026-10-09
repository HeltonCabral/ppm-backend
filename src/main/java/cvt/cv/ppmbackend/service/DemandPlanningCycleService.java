package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCommitteeActionDtos;
import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.entity.DemandCommitteeAction;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.OperationalPlan;
import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.CommitteeActionType;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandCommitteeActionRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
@Transactional
public class DemandPlanningCycleService {
    private static final Map<DemandPlanningStatus, Set<DemandPlanningStatus>> TRANSITIONS = new HashMap<>();
    static {
        TRANSITIONS.put(DRAFT, Set.of(IN_ANALYSIS, IN_REVIEW, BACKLOG));
        TRANSITIONS.put(IN_ANALYSIS, Set.of(IN_PRIORITIZATION, IN_REVIEW, BACKLOG));
        TRANSITIONS.put(IN_REVIEW, Set.of(DRAFT, IN_ANALYSIS, IN_PRIORITIZATION, PRIORITIZED, BACKLOG));
        TRANSITIONS.put(IN_PRIORITIZATION, Set.of(PRIORITIZED, IN_ANALYSIS, IN_REVIEW, BACKLOG));
        TRANSITIONS.put(PRIORITIZED, Set.of(IN_PLAN, BACKLOG, IN_ANALYSIS, IN_PRIORITIZATION, IN_REVIEW));
        TRANSITIONS.put(IN_PLAN, Set.of(BACKLOG, IN_REVIEW));
        TRANSITIONS.put(BACKLOG, Set.of(IN_ANALYSIS, IN_PRIORITIZATION));
    }

    private final DemandPlanningCycleRepository cycles;
    private final DemandCommitteeActionRepository actions;
    private final StrategicPlanService strategicPlans;
    private final OperationalPlanRepository operationalPlans;
    private final StrategicPillarRepository strategicPillars;
    private final StrategicElementRepository strategicElements;
    private final StrategicObjectiveRepository strategicObjectives;
    private final ProgramRepository programs;
    private final DemandScoreLifecycleService scoreLifecycle;
    private final DemandScoringService scoringService;
    private final DemandHistoryService historyService;

    public DemandPlanningCycleService(DemandPlanningCycleRepository cycles, DemandCommitteeActionRepository actions,
            StrategicPlanService strategicPlans,
            OperationalPlanRepository operationalPlans, StrategicPillarRepository strategicPillars,
            StrategicElementRepository strategicElements, StrategicObjectiveRepository strategicObjectives,
            ProgramRepository programs,
            DemandScoreLifecycleService scoreLifecycle, @Lazy DemandScoringService scoringService,
            DemandHistoryService historyService) {
        this.cycles = cycles;
        this.actions = actions;
        this.strategicPlans = strategicPlans;
        this.operationalPlans = operationalPlans;
        this.strategicPillars = strategicPillars;
        this.strategicElements = strategicElements;
        this.strategicObjectives = strategicObjectives;
        this.programs = programs;
        this.scoreLifecycle = scoreLifecycle;
        this.scoringService = scoringService;
        this.historyService = historyService;
    }

    public DemandPlanningCycle createInitialCycle(Demand demand, UUID strategicPillarId, UUID strategicElementId,
            UUID strategicObjectiveId, String actorId) {
        StrategicPlan plan = strategicPlans.findCurrentPlanningPlan()
                .orElseThrow(() -> new BadRequestException(
                        "Não existe nenhum Plano Estratégico em processo de planeamento para associar a nova Demanda."));
        OperationalPlan operationalPlan = resolveOperationalPlan(plan.getId());

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setOperationalPlan(operationalPlan);
        cycle.setStatus(DRAFT);
        cycle.setCarriedOver(false);
        cycle.setPreviousCycle(null);
        cycle.setCycleEntryAt(Instant.now());
        applyStrategicFraming(cycle, strategicPillarId, strategicElementId, strategicObjectiveId);
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(demand, "PLANNING_CYCLE_CREATED", null, DRAFT.name(),
                "Demanda incluída no ciclo de planeamento " + plan.getName(), actorId, actorId,
                Map.of("strategicPlanId", plan.getId().toString()));
        return saved;
    }

    public DemandPlanningCycle startNewCycleForDemand(Demand demand, DemandPlanningCycle previousCycle,
            StrategicPlan newPlan, String actorId) {
        if (cycles.findByDemand_IdAndStrategicPlan_Id(demand.getId(), newPlan.getId()).isPresent()) {
            throw new BadRequestException("A demanda já possui um ciclo de planeamento no plano " + newPlan.getName());
        }
        OperationalPlan operationalPlan = resolveOperationalPlan(newPlan.getId());

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setDemand(demand);
        cycle.setStrategicPlan(newPlan);
        cycle.setOperationalPlan(operationalPlan);
        cycle.setStatus(IN_ANALYSIS);
        cycle.setCarriedOver(true);
        cycle.setPreviousCycle(previousCycle);
        cycle.setCycleEntryAt(Instant.now());
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(demand, "PLANNING_CYCLE_CARRIED_OVER", previousCycle.getStatus().name(), IN_ANALYSIS.name(),
                "Demanda transitada para o novo ciclo de planeamento " + newPlan.getName(), actorId, actorId,
                Map.of("strategicPlanId", newPlan.getId().toString(), "previousCycleId", previousCycle.getId().toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<DemandPlanningCycle> getCurrentPlanningCycle(UUID demandId) {
        Optional<DemandPlanningCycle> inPlanning = strategicPlans.findCurrentPlanningPlan()
                .flatMap(plan -> cycles.findByDemand_IdAndStrategicPlan_Id(demandId, plan.getId()));
        if (inPlanning.isPresent()) {
            return inPlanning;
        }
        return strategicPlans.findActivePlan()
                .flatMap(plan -> cycles.findByDemand_IdAndStrategicPlan_Id(demandId, plan.getId()));
    }

    @Transactional(readOnly = true)
    public DemandPlanningCycle resolveForMutation(UUID demandId, UUID strategicPlanId) {
        return cycles.findByDemand_IdAndStrategicPlan_Id(demandId, strategicPlanId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ciclo de planeamento não encontrado para esta demanda no plano informado"));
    }

    public DemandPlanningCycle transitionStatus(DemandPlanningCycle cycle, DemandPlanningStatus to, String reason,
            String actorId) {
        DemandPlanningStatus from = cycle.getStatus();
        if (from == to) {
            return cycle;
        }
        boolean allowed = TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
        if (!allowed) {
            throw domain(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Transição de status inválida: " + from + " -> " + to);
        }
        if (to == BACKLOG && (reason == null || reason.isBlank())) {
            throw new BadRequestException("reason é obrigatório para status " + to);
        }
        if (to == PRIORITIZED) {
            validateCommitteeReadiness(cycle);
        }
        // Submeter o rascunho exige os campos obrigatórios visíveis do Tipo; arquivar ou pedir revisão não.
        if (from == DRAFT && to == IN_ANALYSIS) {
            TypeFieldRules.requireVisibleRequired(cycle.getDemand());
        }
        BigDecimal previousScore = cycle.getScoreTotal();
        scoreLifecycle.applyStatusTransition(cycle, from, to, reason);
        cycle.setStatus(to);
        if (to != PRIORITIZED) {
            cycle.setInStrategicCommittee(false);
        }
        DemandPlanningCycle saved = cycles.save(cycle);
        // Score invalidado na transição liberta o portfolioRank; recompõe para fechar o buraco no ranking.
        if (previousScore != null && saved.getScoreTotal() == null) {
            scoringService.recomputeRanks(saved.getStrategicPlan().getId());
        }
        historyService.log(cycle.getDemand(), "PLANNING_CYCLE_STATUS_CHANGED", from.name(), to.name(), reason,
                actorId, actorId, Map.of("strategicPlanId", cycle.getStrategicPlan().getId().toString()));
        return saved;
    }

    public DemandPlanningCycle sendToStrategicCommittee(DemandPlanningCycle cycle, String reason, String actorId) {
        if (DemandScoreLifecycleService.SCORE_INVALID.equals(cycle.getScoreStatus())) {
            throw domain(HttpStatus.CONFLICT, "SCORE_INVALIDATED_CANNOT_SEND_TO_COMMITTEE",
                    "Recalcule o score antes de enviar");
        }
        if (cycle.getStatus() != PRIORITIZED) {
            cycle = transitionStatus(cycle, PRIORITIZED, reason, actorId);
        }
        cycle.setInStrategicCommittee(true);
        cycle.setStrategicCommitteeAt(Instant.now());
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "SENT_TO_STRATEGIC_COMMITTEE", PRIORITIZED.name(),
                PRIORITIZED.name(), reason, actorId, actorId, Map.of());
        return saved;
    }

    public DemandPlanningCycle assignCommittee(DemandPlanningCycle cycle, Committee committee, String actorId) {
        DemandPlanningStatus from = cycle.getStatus();
        cycle.setResponsibleCommittee(committee);
        scoreLifecycle.applyStatusTransition(cycle, from, IN_PRIORITIZATION, "Comité responsável atribuído");
        cycle.setStatus(IN_PRIORITIZATION);
        cycle.setInStrategicCommittee(false);
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "COMMITTEE_ASSIGNED", from.name(), IN_PRIORITIZATION.name(),
                "Comité responsável atribuído e status alterado para Priorização", actorId, actorId,
                Map.of("committeeId", committee.getId().toString()));
        return saved;
    }

    /**
     * Pedido de revisão de uma Demanda por um Comité especializado ou pelo Comité Estratégico.
     * O ciclo passa a IN_REVIEW, o estado anterior é preservado e a Demanda deixa de poder
     * integrar um cenário final enquanto a revisão estiver aberta.
     */
    public DemandCommitteeAction requestRevision(DemandPlanningCycle cycle, String justification, String actorId,
            String actorName) {
        String reason = trimToNull(justification);
        if (reason == null) {
            throw new BadRequestException("justification é obrigatória no pedido de revisão");
        }
        if (cycle.getStatus() == IN_REVIEW) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_ALREADY_UNDER_REVIEW",
                    "A Demanda já se encontra em revisão");
        }
        DemandPlanningStatus previousStatus = cycle.getStatus();
        transitionStatus(cycle, IN_REVIEW, reason, actorId);
        cycle.setInStrategicCommittee(false);
        DemandPlanningCycle saved = cycles.save(cycle);

        DemandCommitteeAction action = new DemandCommitteeAction();
        action.setPlanningCycle(saved);
        action.setCommittee(cycle.getResponsibleCommittee());
        action.setAction(CommitteeActionType.REQUEST_REVISION);
        action.setJustification(reason);
        action.setRequestedBy(actorOrSystem(actorName, actorId));
        action.setRequestedAt(Instant.now());
        action.setPreviousStatus(previousStatus);
        DemandCommitteeAction savedAction = actions.save(action);
        saved.getCommitteeActions().add(savedAction);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionId", savedAction.getId().toString());
        metadata.put("previousStatus", previousStatus.name());
        historyService.log(cycle.getDemand(), "COMMITTEE_REQUEST_REVISION", previousStatus.name(), IN_REVIEW.name(),
                reason, actorId, actorName, metadata);
        return savedAction;
    }

    /**
     * Conclui a revisão. O ciclo regressa ao estado anterior, exceto quando a revisão alterou
     * dados que influenciam scoring, complexidade, capacidade ou dependências — nesse caso volta
     * a IN_PRIORITIZATION e tem de ser priorizado de novo antes de poder integrar um cenário.
     */
    public DemandCommitteeAction resolveRevision(DemandPlanningCycle cycle, String resolutionNotes,
            boolean requiresRepriorization, String actorId, String actorName) {
        if (cycle.getStatus() != IN_REVIEW) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_NOT_UNDER_REVIEW",
                    "A Demanda não está em revisão");
        }
        DemandCommitteeAction action = actions
                .findFirstByPlanningCycle_IdAndResolvedAtIsNullOrderByRequestedAtDesc(cycle.getId())
                .orElseThrow(() -> domain(HttpStatus.CONFLICT, "NO_OPEN_REVISION",
                        "Não existe nenhum pedido de revisão em aberto para esta Demanda"));

        DemandPlanningStatus target = resolveTargetStatus(cycle, action.getPreviousStatus(), requiresRepriorization);
        transitionStatus(cycle, target, "Revisão concluída", actorId);

        action.setResolvedBy(actorOrSystem(actorName, actorId));
        action.setResolvedAt(Instant.now());
        action.setResolutionNotes(trimToNull(resolutionNotes));
        DemandCommitteeAction savedAction = actions.save(action);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionId", savedAction.getId().toString());
        metadata.put("requiresRepriorization", String.valueOf(requiresRepriorization));
        historyService.log(cycle.getDemand(), "COMMITTEE_REVISION_RESOLVED", IN_REVIEW.name(), target.name(),
                savedAction.getResolutionNotes(), actorId, actorName, metadata);
        return savedAction;
    }

    /**
     * Uma revisão que invalidou o score obriga sempre a nova priorização — o regresso direto a
     * PRIORITIZED só é possível com score válido.
     */
    private DemandPlanningStatus resolveTargetStatus(DemandPlanningCycle cycle, DemandPlanningStatus previousStatus,
            boolean requiresRepriorization) {
        if (requiresRepriorization) {
            return IN_PRIORITIZATION;
        }
        if (previousStatus == PRIORITIZED || previousStatus == IN_PLAN) {
            return scoreLifecycle.isValid(cycle) ? PRIORITIZED : IN_PRIORITIZATION;
        }
        return previousStatus;
    }

    @Transactional(readOnly = true)
    public List<DemandCommitteeActionDtos.Response> actionHistory(UUID planningCycleId) {
        return actions.findByPlanningCycle_IdOrderByRequestedAtDesc(planningCycleId).stream()
                .map(DemandPlanningCycleService::mapAction)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasOpenRevision(UUID planningCycleId) {
        return actions.findFirstByPlanningCycle_IdAndResolvedAtIsNullOrderByRequestedAtDesc(planningCycleId)
                .isPresent();
    }

    public static DemandCommitteeActionDtos.Response mapAction(DemandCommitteeAction action) {
        Committee committee = action.getCommittee();
        return new DemandCommitteeActionDtos.Response(
                action.getId(),
                action.getPlanningCycle().getId(),
                action.getPlanningCycle().getDemand().getId(),
                committee == null ? null : committee.getId(),
                committee == null ? null : committee.getName(),
                action.getAction(),
                action.getJustification(),
                action.getRequestedBy(),
                action.getRequestedAt(),
                action.getResolvedBy(),
                action.getResolvedAt(),
                action.getResolutionNotes(),
                action.getPreviousStatus(),
                action.isOpen(),
                action.getCreatedAt());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actorOrSystem(String actorName, String actorId) {
        String resolved = trimToNull(actorName) != null ? trimToNull(actorName) : trimToNull(actorId);
        return resolved == null ? "system" : resolved;
    }



    /**
     * Renumera de forma contígua (1..n) o ranking de portfólio das Demandas que permanecem
     * no cenário ativo do Plano Estratégico, preservando a ordem relativa existente.
     * Apenas ciclos IN_PLAN integram o ranking ativo.
     */
    public void normalizePortfolioRanks(UUID strategicPlanId, UUID excludedCycleId) {
        Specification<DemandPlanningCycle> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                cb.equal(root.get("status"), IN_PLAN),
                cb.isNotNull(root.get("portfolioRank")));

        List<DemandPlanningCycle> ranked = new ArrayList<>(cycles.findAll(spec));
        if (excludedCycleId != null) {
            ranked.removeIf(c -> excludedCycleId.equals(c.getId()));
        }
        ranked.sort(Comparator.comparingInt(DemandPlanningCycle::getPortfolioRank));

        int rank = 1;
        for (DemandPlanningCycle c : ranked) {
            c.setPortfolioRank(rank++);
        }
        cycles.saveAll(ranked);
    }

    public DemandPlanningCycle convertToProject(DemandPlanningCycle cycle, Project project, String actorId) {
        validateConvertible(cycle);
        if (!scoreLifecycle.isValid(cycle)) {
            throw domain(HttpStatus.CONFLICT, "CONVERSION_REQUIRES_VALID_SCORE",
                    "A demanda aprovada deve possuir um score válido antes da conversão");
        }
        DemandPlanningStatus from = cycle.getStatus();
        cycle.setConvertedProject(project);
        cycle.setConvertedAt(Instant.now());
        cycle.setConvertedBy(actorId);
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "CONVERTED_TO_PROJECT", from.name(), cycle.getStatus().name(),
                "Demanda convertida em projeto", actorId, actorId, Map.of("projectId", project.getId().toString()));
        return saved;
    }

    public DemandPlanningCycle convertToProgram(DemandPlanningCycle cycle, Program program, String actorId) {
        validateConvertible(cycle);
        DemandPlanningStatus from = cycle.getStatus();
        cycle.setConvertedProgram(program);
        cycle.setConvertedAt(Instant.now());
        cycle.setConvertedBy(actorId);
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "CONVERTED_TO_PROGRAM", from.name(), cycle.getStatus().name(),
                "Demanda convertida em programa", actorId, actorId, Map.of("programId", program.getId().toString()));
        return saved;
    }

    private void validateConvertible(DemandPlanningCycle cycle) {
        if (cycle.getStatus() != IN_PLAN) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_NOT_IN_PLAN",
                    "Somente Demandas incluídas no plano (IN_PLAN) podem ser convertidas");
        }
        if (cycle.getConvertedProject() != null || cycle.getConvertedProgram() != null) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_ALREADY_CONVERTED", "Demanda já convertida neste ciclo");
        }
    }

    public DemandPlanningCycle updateEnrichment(DemandPlanningCycle cycle, UUID strategicPillarId,
            UUID strategicElementId, UUID strategicObjectiveId, UUID programId, String capacityStatus,
            String riskStatus, String actorId) {
        applyStrategicFraming(cycle, strategicPillarId, strategicElementId, strategicObjectiveId);
        if (programId != null) {
            cycle.setProgram(programs.findById(programId)
                    .orElseThrow(() -> new ResourceNotFoundException("Programa não encontrado: " + programId)));
        }
        if (capacityStatus != null) {
            cycle.setCapacityStatus(capacityStatus);
        }
        if (riskStatus != null) {
            cycle.setRiskStatus(riskStatus);
        }
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "PLANNING_CYCLE_ENRICHED", cycle.getStatus().name(), cycle.getStatus().name(),
                "Enquadramento do ciclo atualizado", actorId, actorId, Map.of());
        return saved;
    }

    public void finalizePlanningCycle(UUID strategicPlanId, String actorId) {
        List<DemandPlanningCycle> all = cycles.findAllByStrategicPlan_Id(strategicPlanId);
        Set<DemandPlanningStatus> keep = Set.of(IN_PLAN, BACKLOG);
        for (DemandPlanningCycle cycle : all) {
            if (!keep.contains(cycle.getStatus())) {
                transitionStatus(cycle, BACKLOG, "Ciclo de planeamento encerrado", actorId);
            }
        }
    }

    private void applyStrategicFraming(DemandPlanningCycle cycle, UUID strategicPillarId, UUID strategicElementId,
            UUID strategicObjectiveId) {
        if (strategicPillarId != null) {
            cycle.setStrategicPillar(strategicPillars.findById(strategicPillarId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pilar estratégico não encontrado: " + strategicPillarId)));
        }
        if (strategicElementId != null) {
            cycle.setStrategicElement(strategicElements.findById(strategicElementId)
                    .orElseThrow(() -> new ResourceNotFoundException("Elemento estratégico não encontrado: " + strategicElementId)));
        }
        if (strategicObjectiveId != null) {
            cycle.setStrategicObjective(strategicObjectives.findById(strategicObjectiveId)
                    .orElseThrow(() -> new ResourceNotFoundException("Objetivo estratégico não encontrado: " + strategicObjectiveId)));
        }
        if (cycle.getStrategicElement() != null && cycle.getStrategicPillar() != null
                && !cycle.getStrategicElement().getStrategicPillar().getId().equals(cycle.getStrategicPillar().getId())) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "ELEMENT_DOES_NOT_BELONG_TO_PILLAR",
                    "O elemento estratégico não pertence ao pilar informado");
        }
        if (cycle.getStrategicObjective() != null && cycle.getStrategicElement() != null
                && !cycle.getStrategicObjective().getStrategicElement().getId().equals(cycle.getStrategicElement().getId())) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "OBJECTIVE_DOES_NOT_BELONG_TO_ELEMENT",
                    "O objetivo estratégico não pertence ao elemento informado");
        }
    }

    private OperationalPlan resolveOperationalPlan(UUID strategicPlanId) {
        List<OperationalPlan> plans = operationalPlans.findByStrategicPlan_Id(strategicPlanId);
        if (plans.isEmpty()) {
            throw new BadRequestException("Não existe Plano Operacional criado para o Plano Estratégico em planeamento.");
        }
        return plans.stream().max((a, b) -> a.getFiscalYear().compareTo(b.getFiscalYear())).orElseThrow();
    }

    private void validateCommitteeReadiness(DemandPlanningCycle cycle) {
        Committee responsible = cycle.getResponsibleCommittee();
        if (responsible == null) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "RESPONSIBLE_COMMITTEE_REQUIRED",
                    "O comité responsável é obrigatório para o ciclo ficar pronto para Comité.");
        }
        if (responsible.getStatus() != CommitteeStatus.ACTIVE) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "COMMITTEE_INACTIVE", "O comité responsável deve estar ativo.");
        }
    }

    private DomainException domain(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message, Map.of());
    }
}
