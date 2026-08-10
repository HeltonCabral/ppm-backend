package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.*;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.DemandScoringResponse;
import cvt.cv.ppmbackend.dto.CommitteeSuggestionResponse;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.Priority;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class DemandService {
    private static final String STATUS_IN_ANALYSIS = "IN_ANALYSIS";
    private static final Set<String> PRIORITY_CODES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> EFFORT_CODES = Set.of("XS", "S", "M", "L", "XL");
        private static final Set<String> STATUS_CODES = Set.of(STATUS_IN_ANALYSIS, "IN_PRIORIZATION",
            "IN_PRIORITIZATION",
            "PRIORITIZED", "APPROVED", "REJECTED", "CONVERTED_TO_PROJECT", "ARCHIVED",
            "UNDER_PRIORITIZATION", "READY_FOR_COMMITTEE");
    private static final Set<String> CAPACITY_CODES = Set.of("NOT_ANALYZED", "AVAILABLE", "LIMITED", "UNAVAILABLE");
    private static final Set<String> RISK_CODES = Set.of("NOT_EVALUATED", "LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> APPROVAL_CODES = Set.of("NORMAL", "CONDITIONAL");
    private static final Set<String> COMMITTEE_CODES = Set.of("APPROVED", "CONDITIONALLY_APPROVED", "REVISION_REQUESTED",
            "DEFERRED", "REJECTED", "REFERRED_TO_OPERATIONS");

    private final DemandRepository demands;
    private final DemandAttachmentRepository attachments;
    private final DemandHistoryRepository history;
    private final DemandCodeService codeService;
    private final DemandHistoryService historyService;
    private final StrategicPlanService strategicPlans;
    private final OperationalPlanService operationalPlans;
    private final StrategicPillarService pillars;
    private final StrategicObjectiveService objectives;
    private final ProgramService programs;
    private final CommitteeService committees;
    private final CommitteeSuggestionService committeeSuggestions;
    private final ProjectRepository projects;
    private final LookupValueService lookups;
    private final DemandScoringService scoringService;
    private final DemandScoreLifecycleService scoreLifecycle;

    public DemandService(DemandRepository demands, DemandAttachmentRepository attachments, DemandHistoryRepository history,
            DemandCodeService codeService, DemandHistoryService historyService, StrategicPlanService strategicPlans,
            OperationalPlanService operationalPlans, StrategicPillarService pillars, StrategicObjectiveService objectives,
            ProgramService programs, CommitteeService committees, CommitteeSuggestionService committeeSuggestions,
            ProjectRepository projects, LookupValueService lookups,
            DemandScoringService scoringService, DemandScoreLifecycleService scoreLifecycle) {
        this.demands = demands;
        this.attachments = attachments;
        this.history = history;
        this.codeService = codeService;
        this.historyService = historyService;
        this.strategicPlans = strategicPlans;
        this.operationalPlans = operationalPlans;
        this.pillars = pillars;
        this.objectives = objectives;
        this.programs = programs;
        this.committees = committees;
        this.committeeSuggestions = committeeSuggestions;
        this.projects = projects;
        this.lookups = lookups;
        this.scoringService = scoringService;
        this.scoreLifecycle = scoreLifecycle;
    }

    public DemandResponse create(Create req, String actorId) {
        Demand demand = new Demand();
        demand.setCode(codeService.nextCode());
        demand.setStatus(STATUS_IN_ANALYSIS);
        demand.setCreatedBy(actor(actorId));
        demand.setUpdatedBy(actor(actorId));
        validateReadOnlyScoreFields(req.scoreTotal(), req.portfolioRank(), req.directionRank(), demand, true);
        apply(req, demand, true);
        Demand saved = demands.save(demand);
        if (req.attachments() != null) {
            req.attachments().forEach(a -> addAttachment(saved, new AttachmentCreate(a.name(), a.url(), a.contentType()),
                    actorId));
        }
        historyService.log(saved, "CREATED", null, saved.getStatus(), "Demanda criada", actor(actorId), actor(actorId),
                Map.of("code", saved.getCode()));
        return map(saved);
    }

    @Transactional
    public DemandResponse update(UUID id, Update req, String actorId) {
        Demand demand = entity(id);
        String old = demand.getStatus();
        BigDecimal previousScore = demand.getScoreTotal();
        String previousDirection = demand.getDirection();
        ScoreRelevantState previousScoreInputs = scoreRelevantState(demand);
        validateReadOnlyScoreFields(req.scoreTotal(), req.portfolioRank(), req.directionRank(), demand, false);
        apply(toCreate(req), demand, false);
        handleScoreRelevantChanges(demand, previousScoreInputs);
        demand.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(demand);
        recomputeRanksIfNeeded(previousScore, previousDirection, saved);
        historyService.log(saved, "UPDATED", old, saved.getStatus(), "Demanda atualizada", actor(actorId),
                actor(actorId), Map.of());
        return map(saved);
    }

    @Transactional
    public DemandResponse patch(UUID id, Patch req, String actorId) {
        Demand d = entity(id);
        BigDecimal previousScore = d.getScoreTotal();
        String previousDirection = d.getDirection();
        ScoreRelevantState previousScoreInputs = scoreRelevantState(d);
        validateReadOnlyScoreFields(req.scoreTotal(), req.portfolioRank(), req.directionRank(), d, false);
        Create merged = mergePatch(d, req);
        String old = d.getStatus();
        apply(merged, d, false);
        handleScoreRelevantChanges(d, previousScoreInputs);
        d.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(d);
        recomputeRanksIfNeeded(previousScore, previousDirection, saved);
        historyService.log(saved, "UPDATED", old, saved.getStatus(), "Demanda atualizada parcialmente", actor(actorId),
                actor(actorId), Map.of());
        return map(saved);
    }

    @Transactional
    public void delete(UUID id, String actorId) {
        Demand d = entity(id);
        BigDecimal previousScore = d.getScoreTotal();
        String previousDirection = d.getDirection();
        String oldStatus = norm(d.getStatus());
        scoreLifecycle.applyStatusTransition(d, oldStatus, "ARCHIVED", "Demanda arquivada por eliminação lógica");
        d.setStatus("ARCHIVED");
        d.setDeletedAt(Instant.now());
        d.setUpdatedBy(actor(actorId));
        recomputeRanksIfNeeded(previousScore, previousDirection, d);
        historyService.log(d, "ARCHIVED", oldStatus, "ARCHIVED", "Demanda arquivada por eliminação lógica",
                actor(actorId), actor(actorId), Map.of());
    }

    @Transactional
    public DemandResponse changeStatus(UUID id, StatusPatch req, String actorId) {
        Demand d = entity(id);
        BigDecimal previousScore = d.getScoreTotal();
        String previousDirection = d.getDirection();
        String from = norm(d.getStatus());
        String to = norm(req.status());
        validateStatusValue(to);
        validateTransition(d, from, to, req.reason());
        scoreLifecycle.applyStatusTransition(d, from, to, req.reason());
        d.setStatus(to);
        d.setUpdatedBy(actor(actorId));
        if ("APPROVED".equals(to) && d.getCommitteeDecision() == null)
            d.setCommitteeDecision("APPROVED");
        if ("REJECTED".equals(to)) {
            d.setCommitteeDecision("REJECTED");
            d.setRejectionReason(req.reason());
        }
        if (STATUS_IN_ANALYSIS.equals(to) && "REJECTED".equals(from)) {
            d.setCommitteeDecision(null);
            d.setRejectionReason(null);
        }
        Demand saved = demands.save(d);
        recomputeRanksIfNeeded(previousScore, previousDirection, saved);
        String event = eventTypeForTransition(to);
        historyService.log(saved, event, from, to, req.reason(), actor(actorId), actor(actorId), Map.of());
        return map(saved);
    }

    public CommitteeSuggestionResponse committeeSuggestion(UUID id) {
        return committeeSuggestions.suggest(entity(id));
    }

    @Transactional
    public CommitteeSuggestionResponse applyCommitteeSuggestion(UUID id, String actorId) {
        Demand demand = entity(id);
        CommitteeSuggestionService.SuggestionResult result = committeeSuggestions.calculate(demand);
        Committee suggested = result.suggestedCommittee();
        demand.setSuggestedCommittee(suggested);
        if (demand.getResponsibleCommittee() == null && suggested != null) {
            demand.setResponsibleCommittee(suggested);
            demand.setCommitteeChangeJustification(null);
        }
        demand.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(demand);
        historyService.log(saved, "COMMITTEE_SUGGESTION_APPLIED", saved.getStatus(), saved.getStatus(),
                suggested == null ? "Nenhum comité sugerido" : "Sugestão de comité aplicada",
                actor(actorId), actor(actorId),
                suggested == null ? Map.of("score", result.response().score())
                        : Map.of("committeeId", suggested.getId().toString(), "score", result.response().score()));
        return result.response();
    }

    @Transactional
    public DemandResponse confirmCommittee(UUID id, ConfirmCommitteeRequest request, String actorId) {
        Demand demand = entity(id);
        Committee confirmed = committees.findActiveEntityById(request.committeeId());
        boolean differsFromSuggestion = !sameCommittee(confirmed, demand.getSuggestedCommittee());
        String justification = trimToNull(request.justification());
        if (differsFromSuggestion && justification == null) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "COMMITTEE_CHANGE_JUSTIFICATION_REQUIRED",
                    "A justificação é obrigatória quando o comité confirmado difere do sugerido.",
                    Map.of("committeeId", confirmed.getId().toString()));
        }

        demand.setResponsibleCommittee(confirmed);
        demand.setCommitteeChangeJustification(differsFromSuggestion ? justification : null);
        demand.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(demand);
        historyService.log(saved, "COMMITTEE_CONFIRMED", saved.getStatus(), saved.getStatus(),
                "Comité responsável confirmado", actor(actorId), actor(actorId),
                Map.of("committeeId", confirmed.getId().toString()));
        return map(saved);
    }

    @Transactional
    public DemandAttachmentResponse addAttachment(UUID demandId, AttachmentCreate req, String actorId) {
        Demand d = entity(demandId);
        return addAttachment(d, req, actorId);
    }

    @Transactional
    public void deleteAttachment(UUID demandId, UUID attachmentId, String actorId) {
        entity(demandId);
        DemandAttachment att = attachments.findByIdAndDemandId(attachmentId, demandId)
                .orElseThrow(() -> new ResourceNotFoundException("Anexo não encontrado: " + attachmentId));
        attachments.delete(att);
        historyService.log(entity(demandId), "ATTACHMENT_DELETED", null, null, "Anexo removido", actor(actorId),
                actor(actorId), Map.of("attachmentId", attachmentId.toString()));
    }

    @Transactional
    public ConvertResponse convertToProject(UUID demandId, ConvertToProject req, String actorId) {
        Demand d = entity(demandId);
        BigDecimal previousScore = d.getScoreTotal();
        String previousDirection = d.getDirection();
        if (!"APPROVED".equals(d.getStatus())) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_NOT_APPROVED", "Somente demandas aprovadas podem ser convertidas",
                    Map.of("status", d.getStatus()));
        }
        if (d.getConvertedProject() != null) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_ALREADY_CONVERTED", "Demanda já convertida",
                    Map.of("projectId", d.getConvertedProject().getId()));
        }
        if (!scoreLifecycle.isValid(d)) {
            throw domain(HttpStatus.CONFLICT, "CONVERSION_REQUIRES_VALID_SCORE",
                    "A demanda aprovada deve possuir um score válido antes da conversão",
                    Map.of("scoreStatus", Optional.ofNullable(d.getScoreStatus()).orElse("")));
        }

        Program program = req.programId() != null ? programs.findById(req.programId())
                : Optional.ofNullable(d.getProgram())
                        .orElseThrow(() -> new BadRequestException("programId é obrigatório para conversão"));
        OperationalPlan operationalPlan = req.operationalPlanId() != null
                ? operationalPlans.findById(req.operationalPlanId())
                : d.getOperationalPlan();
        validateConversionOperationalPlan(d, operationalPlan);
        LookupValue domain = resolveConversionDomain(d, req.domainId());
        LookupValue projectType = req.projectTypeId() == null ? null
                : lookups.requireActiveInCategory(req.projectTypeId(), "PROJECT_TYPE");
        LookupValue projectPhase = req.projectPhaseId() == null ? null
                : lookups.requireActiveInCategory(req.projectPhaseId(), "PROJECT_PHASE");
        Supplier mainSupplier = resolveMainSupplier(program, req.mainSupplierId());

        String projectName = firstNonBlank(req.projectName(), d.getTitle());
        String businessArea = firstNonBlank(req.businessArea(), d.getArea());
        if (businessArea == null) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "PROJECT_BUSINESS_AREA_REQUIRED",
                    "businessArea é obrigatório para converter a demanda em projeto", Map.of());
        }
        if (req.managerId() != null && (req.projectManager() == null || req.projectManager().isBlank())) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "PROJECT_MANAGER_NAME_REQUIRED",
                    "projectManager é obrigatório quando managerId é informado", Map.of());
        }
        String projectManager = firstNonBlank(req.projectManager(), program.getProgramManager(), actor(actorId));
        LocalDate plannedStartDate = req.plannedStartDate() != null ? req.plannedStartDate() : req.startDate();
        LocalDate plannedEndDate = req.plannedEndDate() != null ? req.plannedEndDate()
                : req.endDate() != null ? req.endDate() : d.getDesiredDate();
        validateDates(plannedStartDate, plannedEndDate);
        validateDates(req.startDate(), req.endDate());
        BigDecimal budget = req.budget() != null ? req.budget() : d.getEstimatedBudget();
        if (budget != null && budget.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("budget não pode ser negativo");
        }
        Integer ranking = req.ranking() != null ? req.ranking() : d.getPortfolioRank();
        if (ranking != null && ranking < 0) {
            throw new BadRequestException("ranking não pode ser negativo");
        }

        Project p = new Project();
        p.setName(projectName);
        p.setDescription(req.description() != null ? req.description() : d.getDescription());
        p.setProgram(program);
        p.setOperationalPlan(operationalPlan);
        p.setDomain(domain);
        p.setBusinessArea(businessArea);
        p.setProjectType(projectType);
        p.setResponsibleDirection(req.responsibleDirection() != null
                ? req.responsibleDirection() : d.getDirection());
        p.setResponsibleTeam(req.responsibleTeam());
        p.setProjectManager(projectManager);
        p.setProjectManagerId(req.managerId());
        p.setStatus(ProjectStatus.DRAFT);
        p.setProjectPhase(projectPhase);
        p.setMainSupplier(mainSupplier);
        if (mainSupplier != null) {
            p.getSuppliers().add(mainSupplier);
        }
        p.setImpactedSystem(req.impactedSystem() != null ? req.impactedSystem() : d.getImpactedSystem());
        p.setScheduleStatus(ExecutiveStatus.GREEN);
        p.setCostStatus(ExecutiveStatus.GREEN);
        p.setRiskStatus(ExecutiveStatus.GREEN);
        p.setValueStatus(ExecutiveStatus.GREEN);
        p.setExpectedBenefits(req.expectedBenefits() != null ? req.expectedBenefits() : d.getExpectedBenefit());
        p.setPlannedStartDate(plannedStartDate);
        p.setStartDate(req.startDate());
        p.setPlannedEndDate(plannedEndDate);
        p.setEndDate(req.endDate());
        p.setPriority(req.priority() != null ? req.priority() : mapPriority(d.getInitialPriority()));
        p.setRanking(ranking);
        p.setBudgetLine(firstNonBlank(req.budgetLine(), domain != null ? domain.getCode() : null));
        p.setBudget(budget);
        p.setPlanType(req.planType());
        p.setDelayReasons(req.delayReasons());
        p.setSourceDemandId(d.getId());

        Project savedProject = projects.save(p);
        scoreLifecycle.applyStatusTransition(d, "APPROVED", "CONVERTED_TO_PROJECT",
                "Demanda convertida em projeto");
        d.setConvertedProject(savedProject);
        d.setStatus("CONVERTED_TO_PROJECT");
        d.setUpdatedBy(actor(actorId));
        Demand savedDemand = demands.save(d);
        recomputeRanksIfNeeded(previousScore, previousDirection, savedDemand);

        historyService.log(savedDemand, "CONVERTED_TO_PROJECT", "APPROVED", "CONVERTED_TO_PROJECT",
                "Demanda convertida em projeto", actor(actorId), actor(actorId),
                Map.of("projectId", savedProject.getId().toString()));

        return new ConvertResponse(
                new DemandConvertInfo(savedDemand.getId(), savedDemand.getCode(), savedDemand.getStatus(),
                        savedProject.getId(), savedDemand.getScoreStatus(), savedDemand.getScoreCalculatedAt(),
                        savedDemand.getScoreInvalidatedAt(), savedDemand.getScoreInvalidationReason(),
                        scoreLifecycle.previousScoreSnapshot(savedDemand)),
                mapProjectConvertInfo(savedProject));
    }

    @Transactional
    public PagedDemandsResponse listPortfolioRanked(int page, int size, String direction, String area) {
        Specification<Demand> spec = (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.isNotNull(root.get("portfolioRank")));
        if (direction != null && !direction.isBlank())
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("direction")), "%" + direction.toLowerCase(Locale.ROOT) + "%"));
        if (area != null && !area.isBlank())
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("area")), "%" + area.toLowerCase(Locale.ROOT) + "%"));
        Page<Demand> p = demands.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "portfolioRank")));
        return new PagedDemandsResponse(p.getContent().stream().map(this::map).toList(), page, size, p.getTotalElements(),
                p.getTotalPages());
    }

    @Transactional
    public PagedDemandsResponse list(int page, int size, String sort, String search, List<String> status, String type,
            String origin, String initialPriority, String urgency, UUID strategicPlanId, UUID operationalPlanId,
            UUID strategicPillarId, UUID strategicObjectiveId, UUID programId, String requester, String businessArea,
            LocalDate createdFrom, LocalDate createdTo) {
        String sortField = sort == null || sort.isBlank() ? "createdAt" : sort;
        Specification<Demand> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("requester")), like),
                    cb.like(cb.lower(root.get("area")), like),
                    cb.like(cb.lower(root.get("easyVistaRef")), like)));
        }
        if (status != null && !status.isEmpty()) {
            Set<String> normalizedStatuses = status.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(this::norm)
                    .collect(java.util.stream.Collectors.toSet());
            if (!normalizedStatuses.isEmpty()) {
                spec = spec.and((r, q, cb) -> r.get("status").in(normalizedStatuses));
            }
        }
        if (type != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("type").get("code")), norm(type)));
        if (origin != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("origin")), norm(origin)));
        if (initialPriority != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("initialPriority")), norm(initialPriority)));
        if (urgency != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("urgency")), norm(urgency)));
        if (strategicPlanId != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("strategicPlan").get("id"), strategicPlanId));
        if (operationalPlanId != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("operationalPlan").get("id"), operationalPlanId));
        if (strategicPillarId != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("strategicPillar").get("id"), strategicPillarId));
        if (strategicObjectiveId != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("strategicObjective").get("id"), strategicObjectiveId));
        if (programId != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("program").get("id"), programId));
        if (requester != null)
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("requester")), "%" + requester.toLowerCase() + "%"));
        if (businessArea != null)
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("area")), "%" + businessArea.toLowerCase() + "%"));
        if (createdFrom != null) {
            Instant from = createdFrom.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        }
        if (createdTo != null) {
            Instant to = createdTo.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("createdAt"), to));
        }

        Page<Demand> p = demands.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortField)));
        return new PagedDemandsResponse(p.getContent().stream().map(this::map).toList(), page, size, p.getTotalElements(),
                p.getTotalPages());
    }

    @Transactional
    public DemandResponse get(UUID id) {
        return map(entity(id));
    }

    @Transactional
    public List<DemandHistoryResponse> history(UUID id) {
        entity(id);
        return history.findByDemandIdOrderByOccurredAtDesc(id).stream()
                .map(h -> new DemandHistoryResponse(h.getId(), h.getEventType(), h.getPreviousStatus(), h.getNewStatus(),
                        h.getDescription(), h.getActorId(), h.getActorName(), h.getOccurredAt(), h.getMetadata()))
                .toList();
    }

    private DemandAttachmentResponse addAttachment(Demand demand, AttachmentCreate req, String actorId) {
        DemandAttachment att = new DemandAttachment();
        att.setDemand(demand);
        att.setName(req.name());
        att.setUrl(req.url());
        att.setContentType(req.contentType());
        att.setCreatedBy(actor(actorId));
        DemandAttachment saved = attachments.save(att);
        historyService.log(demand, "ATTACHMENT_ADDED", null, null, "Anexo adicionado", actor(actorId), actor(actorId),
                Map.of("attachmentId", saved.getId().toString()));
        return new DemandAttachmentResponse(saved.getId(), saved.getName(), saved.getUrl(), saved.getContentType(),
                saved.getCreatedAt(), saved.getCreatedBy());
    }

    private void apply(Create req, Demand d, boolean creating) {
        String normalizedOrigin = req.origin() == null || req.origin().isBlank() ? "MANUAL" : norm(req.origin());
        d.setTitle(req.title());
        d.setDescription(req.description());
        d.setRequester(req.requester());
        d.setArea(req.area());
        d.setDirection(req.direction());
        d.setSponsor(req.sponsor());
        d.setType(req.typeId() == null ? null : lookups.requireActiveInCategory(req.typeId(), "DEMAND_TYPE"));
        d.setOrigin(normalizedOrigin);
        d.setEasyVistaRef(req.easyVistaRef());
        d.setStrategicPlan(req.strategicPlanId() == null ? null : strategicPlans.findById(req.strategicPlanId()));
        d.setOperationalPlan(req.operationalPlanId() == null ? null : operationalPlans.findById(req.operationalPlanId()));
        d.setStrategicPillar(req.strategicPillarId() == null ? null : pillars.findById(req.strategicPillarId()));
        d.setStrategicObjective(
                req.strategicObjectiveId() == null ? null : objectives.findById(req.strategicObjectiveId()));
        d.setProgram(req.programId() == null ? null : programs.findById(req.programId()));
        Committee assignedCommittee = req.committeeId() == null ? null
                : committees.findActiveEntityById(req.committeeId());
        d.setSuggestedCommittee(assignedCommittee);
        d.setResponsibleCommittee(assignedCommittee);
        d.setCommitteeChangeJustification(null);
        d.setDomain(req.domainId() == null ? null : lookups.findById(req.domainId()));
        d.setImpactedSystem(req.impactedSystem());
        d.setInitialPriority(req.initialPriority() == null ? null : norm(req.initialPriority()));
        d.setEstimatedEffort(req.estimatedEffort() == null ? null : norm(req.estimatedEffort()));
        d.setExpectedImpact(req.expectedImpact());
        d.setExpectedBenefit(req.expectedBenefit());
        d.setUrgency(req.urgency() == null ? null : norm(req.urgency()));
        d.setEstimatedBudget(req.estimatedBudget());
        d.setDesiredDate(req.desiredDate());
        d.setNotes(req.notes());
        d.setCapacityStatus(req.capacityStatus() == null ? "NOT_ANALYZED" : norm(req.capacityStatus()));
        d.setRiskStatus(req.riskStatus() == null ? "NOT_EVALUATED" : norm(req.riskStatus()));
        d.setRisksIdentified(req.risksIdentified());
        d.setDependenciesIdentified(req.dependenciesIdentified());
       
        d.setApprovalType(req.approvalType() == null ? null : norm(req.approvalType()));
        d.setCommitteeDecision(req.committeeDecision() == null ? null : norm(req.committeeDecision()));
        d.setRejectionReason(req.rejectionReason());

        if (creating)
            d.setStatus(STATUS_IN_ANALYSIS);

        validateBusinessRules(d);
    }

    private void validateBusinessRules(Demand d) {
        if ("EASYVISTA".equals(norm(d.getOrigin())) && (d.getEasyVistaRef() == null || d.getEasyVistaRef().isBlank())) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "EASYVISTA_REFERENCE_REQUIRED",
                    "easyVistaRef é obrigatório quando origin = EASYVISTA", Map.of());
        }
        if (d.getEstimatedBudget() != null && d.getEstimatedBudget().compareTo(BigDecimal.ZERO) < 0)
            throw new BadRequestException("estimatedBudget não pode ser negativo");

        if (d.getType() == null) {
            throw new BadRequestException("typeId é obrigatório");
        }
        validateLookupOrEnum("DEMAND_ORIGIN", d.getOrigin(), Set.of("MANUAL", "EASYVISTA"));
        validateLookupOrEnum("PRIORITY", d.getInitialPriority(), PRIORITY_CODES);
        validateLookupOrEnum("ESTIMATED_EFFORT", d.getEstimatedEffort(), EFFORT_CODES);
        validateLookupOrEnum("URGENCY", d.getUrgency(), PRIORITY_CODES);
        validateLookupOrEnum("DEMAND_STATUS", d.getStatus(), STATUS_CODES);
        validateLookupOrEnum("CAPACITY_STATUS", d.getCapacityStatus(), CAPACITY_CODES);
        validateLookupOrEnum("RISK_STATUS", d.getRiskStatus(), RISK_CODES);
        validateLookupOrEnum("APPROVAL_TYPE", d.getApprovalType(), APPROVAL_CODES);
        validateLookupOrEnum("COMMITTEE_DECISION", d.getCommitteeDecision(), COMMITTEE_CODES);

        if ("READY_FOR_COMMITTEE".equals(norm(d.getStatus()))) {
            validateCommitteeReadiness(d);
        }

        if (d.getStrategicPillar() != null && d.getStrategicObjective() != null
                && d.getStrategicObjective().getStrategicElement() != null
                && d.getStrategicObjective().getStrategicElement().getStrategicPillar() != null
                && !Objects.equals(d.getStrategicPillar().getId(),
                        d.getStrategicObjective().getStrategicElement().getStrategicPillar().getId())) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "OBJECTIVE_DOES_NOT_BELONG_TO_PILLAR",
                    "Objetivo não pertence ao pilar informado", Map.of());
        }

        
    }

    private void validateLookupOrEnum(String category, String code, Set<String> fallback) {
        if (code == null || code.isBlank())
            return;
        boolean valid = lookups.existsCode(category, code) || (fallback != null && fallback.contains(norm(code)));
        if (!valid)
            throw new BadRequestException("Valor inválido para " + category + ": " + code);
    }

    private void validateStatusValue(String status) {
        if (!STATUS_CODES.contains(status))
            throw new BadRequestException("status inválido: " + status);
    }

    private void validateTransition(Demand demand, String from, String to, String reason) {
        if (Objects.equals(from, to))
            return;
        if ("APPROVED".equals(from) && STATUS_IN_ANALYSIS.equals(to)) {
            throw domain(HttpStatus.CONFLICT, "APPROVED_DEMAND_CANNOT_RETURN_TO_ANALYSIS",
                    "Uma demanda aprovada não pode voltar para Em Análise",
                    Map.of("from", from, "to", to));
        }
        if ("CONVERTED_TO_PROJECT".equals(to)) {
            throw domain(HttpStatus.CONFLICT, "PROJECT_CONVERSION_ENDPOINT_REQUIRED",
                    "Use o endpoint de conversão para alterar a demanda para Convertida em Projeto",
                    Map.of("from", from, "to", to));
        }
        Map<String, Set<String>> next = new HashMap<>();
        next.put(STATUS_IN_ANALYSIS, Set.of("IN_PRIORIZATION", "REJECTED", "ARCHIVED"));
        next.put("IN_PRIORIZATION", Set.of("PRIORITIZED", "REJECTED", "ARCHIVED", STATUS_IN_ANALYSIS));
        next.put("PRIORITIZED", Set.of("APPROVED", "REJECTED", STATUS_IN_ANALYSIS, "IN_PRIORIZATION", "ARCHIVED"));
        next.put("UNDER_PRIORITIZATION", Set.of("PRIORITIZED", "READY_FOR_COMMITTEE", "REJECTED", "ARCHIVED", STATUS_IN_ANALYSIS));
        next.put("READY_FOR_COMMITTEE",
            Set.of("APPROVED", "REJECTED", STATUS_IN_ANALYSIS, "IN_PRIORIZATION", "UNDER_PRIORITIZATION", "ARCHIVED"));
        next.put("APPROVED", Set.of("CONVERTED_TO_PROJECT", "ARCHIVED"));
        next.put("REJECTED", Set.of(STATUS_IN_ANALYSIS, "ARCHIVED"));
        next.put("CONVERTED_TO_PROJECT", Set.of("ARCHIVED"));
        next.put("ARCHIVED", Set.of());

        boolean ok = next.getOrDefault(from, Set.of()).contains(to);
        if (!ok) {
            throw domain(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Transição de status inválida",
                    Map.of("from", from, "to", to));
        }

        if ("READY_FOR_COMMITTEE".equals(to)) {
            validateCommitteeReadiness(demand);
        }

        if (("REJECTED".equals(to) || "ARCHIVED".equals(to)) && (reason == null || reason.isBlank())) {
            throw new BadRequestException("reason é obrigatório para status " + to);
        }
    }

    private String eventTypeForTransition(String to) {
        return switch (to) {
            case "PRIORITIZED", "READY_FOR_COMMITTEE" -> "SUBMITTED_TO_COMMITTEE";
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            default -> "STATUS_CHANGED";
        };
    }

    private Demand entity(UUID id) {
        return demands.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> domain(HttpStatus.NOT_FOUND, "DEMAND_NOT_FOUND",
                "Demanda não encontrada", Map.of("id", id.toString())));
    }

    private DemandResponse map(Demand d) {
        List<DemandAttachmentResponse> att = attachments.findByDemandIdOrderByCreatedAtDesc(d.getId()).stream()
                .map(a -> new DemandAttachmentResponse(a.getId(), a.getName(), a.getUrl(), a.getContentType(),
                        a.getCreatedAt(), a.getCreatedBy()))
                .toList();
        String typeCode = d.getType() != null ? d.getType().getCode() : null;
        String domainCode = d.getDomain() != null ? d.getDomain().getCode() : null;
        LookupValueSummary typeData = mapLookupValue(d.getType());
        LookupValueSummary domainData = mapLookupValue(d.getDomain());
        StrategicPlanSummary strategicPlan = mapStrategicPlan(d.getStrategicPlan());
        OperationalPlanSummary operationalPlan = mapOperationalPlan(d.getOperationalPlan());
        StrategicPillarSummary strategicPillar = mapStrategicPillar(d.getStrategicPillar());
        StrategicObjectiveSummary strategicObjective = mapStrategicObjective(d.getStrategicObjective());
        ProgramSummary program = mapProgram(d.getProgram());
        CommitteeSummary suggestedCommittee = mapCommittee(d.getSuggestedCommittee());
        CommitteeSummary responsibleCommittee = mapCommittee(d.getResponsibleCommittee());
        CommitteeSummary committee = responsibleCommittee;
        ProjectSummary convertedProject = mapProject(d.getConvertedProject());
        DemandScoringResponse calculatedScoring = scoringService.getByDemand(d.getId());
        return new DemandResponse(d.getId(), d.getCode(), d.getTitle(), d.getDescription(), d.getRequester(), d.getArea(),
                d.getDirection(), d.getSponsor(), d.getTypeId(), typeCode, d.getOrigin(), d.getEasyVistaRef(),
                d.getStrategicPlanId(),
                d.getOperationalPlanId(), d.getStrategicPillarId(), d.getStrategicObjectiveId(), d.getProgramId(),
                d.getCommitteeId(), d.getSuggestedCommitteeId(), d.getResponsibleCommitteeId(),
                d.getCommitteeChangeJustification(), d.getDomainId(), domainCode, d.getImpactedSystem(),
                d.getInitialPriority(), d.getEstimatedEffort(),
                d.getExpectedImpact(),
                d.getExpectedBenefit(), d.getUrgency(), d.getEstimatedBudget(), d.getDesiredDate(), d.getNotes(),
                norm(d.getStatus()), d.getCapacityStatus(), d.getRiskStatus(), d.getRisksIdentified(),
                d.getDependenciesIdentified(), d.getScoreTotal(),
                d.getScoreStatus(), d.getScoreCalculatedAt(), d.getScoreInvalidatedAt(),
                d.getScoreInvalidationReason(), scoreLifecycle.previousScoreSnapshot(d),
                d.getPortfolioRank(), d.getDirectionRank(), d.getApprovalType(), d.getCommitteeDecision(),
                d.getRejectionReason(),
                d.getConvertedProjectId(), d.getCreatedAt(), d.getCreatedBy(), d.getUpdatedAt(), d.getUpdatedBy(),
                d.getVersion(), typeData, domainData, strategicPlan, operationalPlan, strategicPillar,
                strategicObjective, program, committee, suggestedCommittee, responsibleCommittee,
                convertedProject, att, calculatedScoring);
    }

    private LookupValueSummary mapLookupValue(LookupValue value) {
        if (value == null)
            return null;
        return new LookupValueSummary(value.getId(), value.getCategory(), value.getCode(), value.getLabel());
    }

    private StrategicPlanSummary mapStrategicPlan(StrategicPlan plan) {
        if (plan == null)
            return null;
        return new StrategicPlanSummary(plan.getId(), plan.getName(), plan.getStartYear(), plan.getEndYear(),
                plan.getStatus() != null ? plan.getStatus().name() : null);
    }

    private OperationalPlanSummary mapOperationalPlan(OperationalPlan plan) {
        if (plan == null)
            return null;
        return new OperationalPlanSummary(plan.getId(), plan.getName(), plan.getFiscalYear(),
                plan.getStatus() != null ? plan.getStatus().name() : null);
    }

    private StrategicPillarSummary mapStrategicPillar(StrategicPillar pillar) {
        if (pillar == null)
            return null;
        return new StrategicPillarSummary(pillar.getId(), pillar.getName(), pillar.getDescription());
    }

    private StrategicObjectiveSummary mapStrategicObjective(StrategicObjective objective) {
        if (objective == null)
            return null;
        return new StrategicObjectiveSummary(objective.getId(), objective.getName(), objective.getFiscalYear(),
                objective.getStartYear(), objective.getEndYear(), objective.getPerspective());
    }

    private ProgramSummary mapProgram(Program program) {
        if (program == null)
            return null;
        return new ProgramSummary(program.getId(), program.getName(), program.getProgramManager());
    }

    private CommitteeSummary mapCommittee(Committee committee) {
        if (committee == null)
            return null;
        return new CommitteeSummary(committee.getId(), committee.getName(),
                committee.getStatus() != null ? committee.getStatus().name() : null,
                committee.isStrategicCommittee());
    }

    private ProjectSummary mapProject(Project project) {
        if (project == null)
            return null;
        return new ProjectSummary(project.getId(), project.getName(),
                project.getStatus() != null ? project.getStatus().name() : null);
    }

    private void validateConversionOperationalPlan(Demand demand, OperationalPlan operationalPlan) {
        if (operationalPlan == null || demand.getStrategicPlan() == null) {
            return;
        }
        StrategicPlan plan = operationalPlan.getStrategicPlan();
        if (plan == null || !Objects.equals(plan.getId(), demand.getStrategicPlan().getId())) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "OPERATIONAL_PLAN_STRATEGIC_PLAN_MISMATCH",
                    "O plano operacional do projeto deve pertencer ao plano estratégico da demanda",
                    Map.of("operationalPlanId", operationalPlan.getId().toString(),
                            "strategicPlanId", demand.getStrategicPlan().getId().toString()));
        }
    }

    private LookupValue resolveConversionDomain(Demand demand, UUID requestedDomainId) {
        LookupValue domain = requestedDomainId != null ? lookups.findById(requestedDomainId) : demand.getDomain();
        if (domain == null) {
            return null;
        }
        Set<String> acceptedCategories = Set.of("PROJECT_DOMAIN", "DEMAND_DOMAIN", "PROGRAM_DOMAIN", "DOMAIN");
        if (!Boolean.TRUE.equals(domain.getActive()) || domain.getCategory() == null
                || !acceptedCategories.contains(norm(domain.getCategory()))) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PROJECT_DOMAIN",
                    "domainId deve referenciar um domínio ativo e compatível com projetos",
                    Map.of("domainId", domain.getId().toString()));
        }
        return domain;
    }

    private Supplier resolveMainSupplier(Program program, UUID mainSupplierId) {
        if (mainSupplierId == null) {
            return null;
        }
        return Optional.ofNullable(program.getSuppliers()).orElseGet(Set::of).stream()
                .filter(supplier -> Objects.equals(supplier.getId(), mainSupplierId))
                .findFirst()
                .orElseThrow(() -> domain(HttpStatus.UNPROCESSABLE_ENTITY, "MAIN_SUPPLIER_NOT_IN_PROGRAM",
                        "mainSupplierId deve identificar um fornecedor associado ao programa selecionado",
                        Map.of("mainSupplierId", mainSupplierId.toString(),
                                "programId", program.getId().toString())));
    }

    private ProjectConvertInfo mapProjectConvertInfo(Project project) {
        return new ProjectConvertInfo(
                project.getId(),
                null,
                project.getName(),
                project.getDescription(),
                project.getProgram() != null ? project.getProgram().getId() : null,
                project.getOperationalPlanId(),
                project.getDomain() != null ? project.getDomain().getId() : null,
                project.getBusinessArea(),
                project.getProjectType() != null ? project.getProjectType().getId() : null,
                project.getResponsibleDirection(),
                project.getResponsibleTeam(),
                project.getProjectManagerId(),
                project.getProjectManager(),
                project.getProjectPhase() != null ? project.getProjectPhase().getId() : null,
                project.getMainSupplier() != null ? project.getMainSupplier().getId() : null,
                project.getImpactedSystem(),
                project.getExpectedBenefits(),
                project.getPlannedStartDate(),
                project.getStartDate(),
                project.getPlannedEndDate(),
                project.getEndDate(),
                project.getPriority(),
                project.getRanking(),
                project.getBudgetLine(),
                project.getBudget(),
                project.getPlanType(),
                project.getDelayReasons());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void validateReadOnlyScoreFields(BigDecimal requestedScore, Integer requestedPortfolioRank,
            Integer requestedDirectionRank, Demand demand, boolean creating) {
        boolean scoreChanged = requestedScore != null
                && (creating || demand.getScoreTotal() == null
                        || requestedScore.compareTo(demand.getScoreTotal()) != 0);
        boolean portfolioRankChanged = requestedPortfolioRank != null
                && (creating || !Objects.equals(requestedPortfolioRank, demand.getPortfolioRank()));
        boolean directionRankChanged = requestedDirectionRank != null
                && (creating || !Objects.equals(requestedDirectionRank, demand.getDirectionRank()));
        if (scoreChanged || portfolioRankChanged || directionRankChanged) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "SCORE_FIELDS_READ_ONLY",
                    "scoreTotal, portfolioRank e directionRank só podem ser alterados pelo endpoint de scoring",
                    Map.of());
        }
    }

    private void validateCommitteeReadiness(Demand demand) {
        Committee responsible = demand.getResponsibleCommittee();
        if (responsible == null) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "RESPONSIBLE_COMMITTEE_REQUIRED",
                    "O comité responsável é obrigatório para a demanda ficar pronta para Comité.", Map.of());
        }
        if (responsible.getStatus() != CommitteeStatus.ACTIVE) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "COMMITTEE_INACTIVE",
                    "O comité responsável deve estar ativo.",
                    Map.of("committeeId", responsible.getId().toString()));
        }
        if (!sameCommittee(responsible, demand.getSuggestedCommittee())
                && trimToNull(demand.getCommitteeChangeJustification()) == null) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "COMMITTEE_CHANGE_JUSTIFICATION_REQUIRED",
                    "A justificação é obrigatória quando o comité responsável difere do sugerido.",
                    Map.of("committeeId", responsible.getId().toString()));
        }
    }

    private boolean sameCommittee(Committee first, Committee second) {
        return first != null && second != null && Objects.equals(first.getId(), second.getId());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void recomputeRanksIfNeeded(BigDecimal previousScore, String previousDirection, Demand demand) {
        boolean leftRanking = previousScore != null && demand.getScoreTotal() == null;
        boolean changedDirection = previousScore != null && demand.getScoreTotal() != null
                && !Objects.equals(normalizeDirection(previousDirection), normalizeDirection(demand.getDirection()));
        if (leftRanking || changedDirection) {
            scoringService.recomputeRanks();
        }
    }

    private String normalizeDirection(String direction) {
        return direction == null || direction.isBlank() ? null : direction.trim().toUpperCase(Locale.ROOT);
    }

    private void handleScoreRelevantChanges(Demand demand, ScoreRelevantState previousState) {
        if (previousState.equals(scoreRelevantState(demand))) {
            return;
        }
        if ("APPROVED".equals(norm(demand.getStatus()))) {
            throw domain(HttpStatus.CONFLICT, "APPROVED_DEMAND_SCORE_INPUTS_LOCKED",
                    "Dados relevantes para scoring não podem ser alterados numa demanda aprovada", Map.of());
        }
        if (scoreLifecycle.isValid(demand)) {
            scoreLifecycle.invalidateAfterRelevantUpdate(demand);
        }
    }

    private ScoreRelevantState scoreRelevantState(Demand demand) {
        return new ScoreRelevantState(
                demand.getDescription(),
                demand.getTypeId(),
                demand.getStrategicPlanId(),
                demand.getOperationalPlanId(),
                demand.getStrategicPillarId(),
                demand.getStrategicObjectiveId(),
                demand.getProgramId(),
                demand.getDomainId(),
                demand.getImpactedSystem(),
                demand.getInitialPriority(),
                demand.getEstimatedEffort(),
                demand.getExpectedImpact(),
                demand.getExpectedBenefit(),
                demand.getUrgency(),
                normalizedDecimal(demand.getEstimatedBudget()),
                demand.getDesiredDate(),
                demand.getCapacityStatus(),
                demand.getRiskStatus(),
                demand.getRisksIdentified(),
                demand.getDependenciesIdentified());
    }

    private BigDecimal normalizedDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private record ScoreRelevantState(
            String description,
            UUID typeId,
            UUID strategicPlanId,
            UUID operationalPlanId,
            UUID strategicPillarId,
            UUID strategicObjectiveId,
            UUID programId,
            UUID domainId,
            String impactedSystem,
            String initialPriority,
            String estimatedEffort,
            String expectedImpact,
            String expectedBenefit,
            String urgency,
            BigDecimal estimatedBudget,
            LocalDate desiredDate,
            String capacityStatus,
            String riskStatus,
            String risksIdentified,
            String dependenciesIdentified) {
    }

    private Create mergePatch(Demand d, Patch p) {
        return new Create(
                p.title() != null ? p.title() : d.getTitle(),
                p.description() != null ? p.description() : d.getDescription(),
                p.requester() != null ? p.requester() : d.getRequester(),
                p.area() != null ? p.area() : d.getArea(),
                p.direction() != null ? p.direction() : d.getDirection(),
                p.sponsor() != null ? p.sponsor() : d.getSponsor(),
                p.typeId() != null ? p.typeId() : d.getTypeId(),
                p.origin() != null ? p.origin() : d.getOrigin(),
                p.easyVistaRef() != null ? p.easyVistaRef() : d.getEasyVistaRef(),
                p.strategicPlanId() != null ? p.strategicPlanId() : d.getStrategicPlanId(),
                p.operationalPlanId() != null ? p.operationalPlanId() : d.getOperationalPlanId(),
                p.strategicPillarId() != null ? p.strategicPillarId() : d.getStrategicPillarId(),
                p.strategicObjectiveId() != null ? p.strategicObjectiveId() : d.getStrategicObjectiveId(),
                p.programId() != null ? p.programId() : d.getProgramId(),
                p.committeeId() != null ? p.committeeId() : d.getCommitteeId(),
                p.domainId() != null ? p.domainId() : d.getDomainId(),
                p.impactedSystem() != null ? p.impactedSystem() : d.getImpactedSystem(),
                p.initialPriority() != null ? p.initialPriority() : d.getInitialPriority(),
                p.estimatedEffort() != null ? p.estimatedEffort() : d.getEstimatedEffort(),
                p.expectedImpact() != null ? p.expectedImpact() : d.getExpectedImpact(),
                p.expectedBenefit() != null ? p.expectedBenefit() : d.getExpectedBenefit(),
                p.urgency() != null ? p.urgency() : d.getUrgency(),
                p.estimatedBudget() != null ? p.estimatedBudget() : d.getEstimatedBudget(),
                p.desiredDate() != null ? p.desiredDate() : d.getDesiredDate(),
                p.notes() != null ? p.notes() : d.getNotes(),
                p.capacityStatus() != null ? p.capacityStatus() : d.getCapacityStatus(),
                p.riskStatus() != null ? p.riskStatus() : d.getRiskStatus(),
                p.risksIdentified() != null ? p.risksIdentified() : d.getRisksIdentified(),
                p.dependenciesIdentified() != null ? p.dependenciesIdentified() : d.getDependenciesIdentified(),
                p.scoreTotal() != null ? p.scoreTotal() : d.getScoreTotal(),
                p.portfolioRank() != null ? p.portfolioRank() : d.getPortfolioRank(),
                p.directionRank() != null ? p.directionRank() : d.getDirectionRank(),
                p.approvalType() != null ? p.approvalType() : d.getApprovalType(),
                p.committeeDecision() != null ? p.committeeDecision() : d.getCommitteeDecision(),
                p.rejectionReason() != null ? p.rejectionReason() : d.getRejectionReason(),
                null);
    }

    private Create toCreate(Update u) {
        return new Create(u.title(), u.description(), u.requester(), u.area(), u.direction(), u.sponsor(), u.typeId(),
                u.origin(), u.easyVistaRef(), u.strategicPlanId(), u.operationalPlanId(), u.strategicPillarId(),
            u.strategicObjectiveId(), u.programId(), u.committeeId(), u.domainId(), u.impactedSystem(), u.initialPriority(),
                u.estimatedEffort(), u.expectedImpact(), u.expectedBenefit(), u.urgency(), u.estimatedBudget(),
                u.desiredDate(), u.notes(), u.capacityStatus(), u.riskStatus(), u.risksIdentified(),
                u.dependenciesIdentified(), u.scoreTotal(),
                u.portfolioRank(), u.directionRank(), u.approvalType(), u.committeeDecision(), u.rejectionReason(),
                null);
    }

    private Priority mapPriority(String value) {
        if (value == null)
            return Priority.MEDIUM;
        return switch (norm(value)) {
            case "LOW" -> Priority.LOW;
            case "HIGH" -> Priority.HIGH;
            case "CRITICAL" -> Priority.CRITICAL;
            default -> Priority.MEDIUM;
        };
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new BadRequestException("endDate não pode ser anterior à startDate");
    }

    private String norm(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("IN_ANALYSYS".equals(normalized)) {
            return STATUS_IN_ANALYSIS;
        }
        if ("IN_PRIORITIZATION".equals(normalized)) {
            return "IN_PRIORIZATION";
        }
        return normalized;
    }

    private String actor(String value) {
        return value == null || value.isBlank() ? "system" : value;
    }

    private DomainException domain(HttpStatus status, String code, String message, Map<String, Object> details) {
        return new DomainException(status, code, message, details);
    }
}
