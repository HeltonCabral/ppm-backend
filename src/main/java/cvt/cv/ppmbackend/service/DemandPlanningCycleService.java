package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCommitteeDecisionDtos;
import cvt.cv.ppmbackend.dto.DemandCommitteeDecisionDtos.DecisionCommand;
import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.entity.DemandCommitteeDecision;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.OperationalPlan;
import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.CommitteeDecision;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandCommitteeDecisionRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.Set;
import java.util.UUID;

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
@Transactional
public class DemandPlanningCycleService {
    private static final Map<DemandPlanningStatus, Set<DemandPlanningStatus>> TRANSITIONS = new HashMap<>();
    static {
        TRANSITIONS.put(DRAFT, Set.of(IN_ANALYSIS, BACKLOG, CLOSED));
        TRANSITIONS.put(IN_ANALYSIS, Set.of(IN_PRIORITIZATION, BACKLOG, CLOSED));
        TRANSITIONS.put(IN_PRIORITIZATION, Set.of(AWAITING_COMMITTEE, BACKLOG, CLOSED, IN_ANALYSIS));
        TRANSITIONS.put(AWAITING_COMMITTEE,
                Set.of(APPROVED, CONDITIONALLY_APPROVED, BACKLOG, IN_ANALYSIS, IN_PRIORITIZATION, CLOSED));
        TRANSITIONS.put(APPROVED, Set.of(CONVERTED_TO_PROJECT, CONVERTED_TO_PROGRAM, CLOSED));
        TRANSITIONS.put(CONDITIONALLY_APPROVED, Set.of(CONVERTED_TO_PROJECT, CONVERTED_TO_PROGRAM, APPROVED, CLOSED));
        TRANSITIONS.put(BACKLOG, Set.of(IN_ANALYSIS, CLOSED));
        TRANSITIONS.put(CONVERTED_TO_PROJECT, Set.of(CLOSED));
        TRANSITIONS.put(CONVERTED_TO_PROGRAM, Set.of(CLOSED));
        TRANSITIONS.put(CLOSED, Set.of());
    }

    private static final Set<CommitteeDecision> REQUIRE_JUSTIFICATION = Set.of(
            CommitteeDecision.CONDITIONALLY_APPROVED, CommitteeDecision.REQUEST_REVISION,
            CommitteeDecision.BACKLOG);

    private final DemandPlanningCycleRepository cycles;
    private final DemandCommitteeDecisionRepository decisions;
    private final StrategicPlanService strategicPlans;
    private final OperationalPlanRepository operationalPlans;
    private final StrategicPillarRepository strategicPillars;
    private final StrategicElementRepository strategicElements;
    private final StrategicObjectiveRepository strategicObjectives;
    private final ProgramRepository programs;
    private final DemandScoreLifecycleService scoreLifecycle;
    private final DemandHistoryService historyService;

    public DemandPlanningCycleService(DemandPlanningCycleRepository cycles, DemandCommitteeDecisionRepository decisions,
            StrategicPlanService strategicPlans,
            OperationalPlanRepository operationalPlans, StrategicPillarRepository strategicPillars,
            StrategicElementRepository strategicElements, StrategicObjectiveRepository strategicObjectives,
            ProgramRepository programs,
            DemandScoreLifecycleService scoreLifecycle, DemandHistoryService historyService) {
        this.cycles = cycles;
        this.decisions = decisions;
        this.strategicPlans = strategicPlans;
        this.operationalPlans = operationalPlans;
        this.strategicPillars = strategicPillars;
        this.strategicElements = strategicElements;
        this.strategicObjectives = strategicObjectives;
        this.programs = programs;
        this.scoreLifecycle = scoreLifecycle;
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
        if (to == CONVERTED_TO_PROJECT || to == CONVERTED_TO_PROGRAM) {
            throw domain(HttpStatus.CONFLICT, "PROJECT_CONVERSION_ENDPOINT_REQUIRED",
                    "Use o endpoint de conversão para alterar o ciclo para Convertido");
        }
        boolean allowed = TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
        if (!allowed) {
            throw domain(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Transição de status inválida: " + from + " -> " + to);
        }
        if ((to == BACKLOG || to == CLOSED) && (reason == null || reason.isBlank())) {
            throw new BadRequestException("reason é obrigatório para status " + to);
        }
        if (to == AWAITING_COMMITTEE) {
            validateCommitteeReadiness(cycle);
        }
        scoreLifecycle.applyStatusTransition(cycle, from, to, reason);
        cycle.setStatus(to);
        if (to != AWAITING_COMMITTEE) {
            cycle.setInStrategicCommittee(false);
        }
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "PLANNING_CYCLE_STATUS_CHANGED", from.name(), to.name(), reason,
                actorId, actorId, Map.of("strategicPlanId", cycle.getStrategicPlan().getId().toString()));
        return saved;
    }

    public DemandPlanningCycle sendToStrategicCommittee(DemandPlanningCycle cycle, String reason, String actorId) {
        if (cycle.getStatus() != AWAITING_COMMITTEE) {
            cycle = transitionStatus(cycle, AWAITING_COMMITTEE, reason, actorId);
        }
        cycle.setInStrategicCommittee(true);
        cycle.setStrategicCommitteeAt(Instant.now());
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "SENT_TO_STRATEGIC_COMMITTEE", AWAITING_COMMITTEE.name(),
                AWAITING_COMMITTEE.name(), reason, actorId, actorId, Map.of());
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
     * Regista uma nova deliberação do Comité e atualiza o status do ciclo em conformidade.
     * As decisões anteriores nunca são sobrescritas — cada deliberação gera um novo
     * {@link DemandCommitteeDecision}, que é a fonte da verdade do histórico.
     */
    public DemandCommitteeDecision applyCommitteeDecision(DemandPlanningCycle cycle, DecisionCommand command) {
        CommitteeDecision decision = command.decision();
        if (decision == null) {
            throw new BadRequestException("Decisão de comité inválida");
        }
        String justification = trimToNull(command.justification());
        String conditions = trimToNull(command.conditions());
        if (justification == null && REQUIRE_JUSTIFICATION.contains(decision)) {
            throw new BadRequestException("justification é obrigatória para a decisão " + decision);
        }

        DemandPlanningStatus from = cycle.getStatus();
        DemandPlanningStatus target = targetStatusOf(decision);
        Integer rankBeforeBacklog = cycle.getPortfolioRank();

        transitionStatus(cycle, target, justification, command.actorId());

        if (decision == CommitteeDecision.BACKLOG) {
            cycle.setRankBeforeBacklog(rankBeforeBacklog);
            cycle.setPortfolioRank(null);
            normalizePortfolioRanks(cycle.getStrategicPlan().getId(), cycle.getId());
        }
        cycle.setInStrategicCommittee(false);
        DemandPlanningCycle saved = cycles.save(cycle);

        DemandCommitteeDecision record = new DemandCommitteeDecision();
        record.setPlanningCycle(saved);
        record.setCommittee(cycle.getResponsibleCommittee());
        record.setDecision(decision);
        record.setJustification(justification);
        record.setConditions(conditions);
        record.setDecisionAt(Instant.now());
        record.setDecidedBy(actorOrSystem(command.actorName(), command.actorId()));
        record.setMeetingReference(trimToNull(command.meetingReference()));
        DemandCommitteeDecision savedDecision = decisions.save(record);
        saved.getCommitteeDecisions().add(savedDecision);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decision", decision.name());
        metadata.put("decisionId", savedDecision.getId().toString());
        if (conditions != null) {
            metadata.put("conditions", conditions);
        }
        if (savedDecision.getMeetingReference() != null) {
            metadata.put("meetingReference", savedDecision.getMeetingReference());
        }
        if (decision == CommitteeDecision.BACKLOG && saved.getRankBeforeBacklog() != null) {
            metadata.put("rankBeforeBacklog", String.valueOf(saved.getRankBeforeBacklog()));
        }
        historyService.log(cycle.getDemand(), "COMMITTEE_DECISION_" + decision.name(), from.name(),
                saved.getStatus().name(), justification, command.actorId(), command.actorName(), metadata);
        return savedDecision;
    }

    @Transactional(readOnly = true)
    public List<DemandCommitteeDecisionDtos.Response> decisionHistory(UUID planningCycleId) {
        return decisions.findByPlanningCycle_IdOrderByDecisionAtDesc(planningCycleId).stream()
                .map(DemandPlanningCycleService::mapDecision)
                .toList();
    }

    public static DemandCommitteeDecisionDtos.Response mapDecision(DemandCommitteeDecision decision) {
        Committee committee = decision.getCommittee();
        return new DemandCommitteeDecisionDtos.Response(
                decision.getId(),
                decision.getPlanningCycle().getId(),
                decision.getPlanningCycle().getDemand().getId(),
                committee == null ? null : committee.getId(),
                committee == null ? null : committee.getName(),
                decision.getDecision(),
                decision.getJustification(),
                decision.getConditions(),
                decision.getDecisionAt(),
                decision.getDecidedBy(),
                decision.getMeetingReference(),
                decision.getCreatedAt());
    }

    private DemandPlanningStatus targetStatusOf(CommitteeDecision decision) {
        return switch (decision) {
            case APPROVED -> APPROVED;
            case CONDITIONALLY_APPROVED -> CONDITIONALLY_APPROVED;
            case REQUEST_REVISION -> IN_ANALYSIS;
            case BACKLOG -> BACKLOG;
        };
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
     * Apenas ciclos APPROVED e CONDITIONALLY_APPROVED integram o ranking ativo.
     */
    public void normalizePortfolioRanks(UUID strategicPlanId, UUID excludedCycleId) {
        Specification<DemandPlanningCycle> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                root.get("status").in(APPROVED, CONDITIONALLY_APPROVED),
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
        scoreLifecycle.applyStatusTransition(cycle, from, CONVERTED_TO_PROJECT, "Demanda convertida em projeto");
        cycle.setStatus(CONVERTED_TO_PROJECT);
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "CONVERTED_TO_PROJECT", from.name(), CONVERTED_TO_PROJECT.name(),
                "Demanda convertida em projeto", actorId, actorId, Map.of("projectId", project.getId().toString()));
        return saved;
    }

    public DemandPlanningCycle convertToProgram(DemandPlanningCycle cycle, Program program, String actorId) {
        validateConvertible(cycle);
        DemandPlanningStatus from = cycle.getStatus();
        cycle.setConvertedProgram(program);
        cycle.setConvertedAt(Instant.now());
        cycle.setConvertedBy(actorId);
        scoreLifecycle.applyStatusTransition(cycle, from, CONVERTED_TO_PROGRAM, "Demanda convertida em programa");
        cycle.setStatus(CONVERTED_TO_PROGRAM);
        DemandPlanningCycle saved = cycles.save(cycle);
        historyService.log(cycle.getDemand(), "CONVERTED_TO_PROGRAM", from.name(), CONVERTED_TO_PROGRAM.name(),
                "Demanda convertida em programa", actorId, actorId, Map.of("programId", program.getId().toString()));
        return saved;
    }

    private void validateConvertible(DemandPlanningCycle cycle) {
        if (cycle.getStatus() != APPROVED && cycle.getStatus() != CONDITIONALLY_APPROVED) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_NOT_APPROVED",
                    "Somente demandas aprovadas podem ser convertidas");
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
        Set<DemandPlanningStatus> keep = Set.of(APPROVED, CONDITIONALLY_APPROVED, CONVERTED_TO_PROJECT,
                CONVERTED_TO_PROGRAM, CLOSED, BACKLOG);
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
