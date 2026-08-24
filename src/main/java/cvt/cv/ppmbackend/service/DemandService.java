package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.*;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos.CurrentCycleSummary;
import cvt.cv.ppmbackend.dto.CommitteeSuggestionResponse;
import cvt.cv.ppmbackend.dto.PreScoreResponse;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
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

import static cvt.cv.ppmbackend.enums.DemandPlanningStatus.*;

@Service
@Transactional
public class DemandService {
    private static final Set<String> PRIORITY_CODES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> EFFORT_CODES = Set.of("LOW", "MEDIUM", "HIGH");

    private final DemandRepository demands;
    private final DemandPlanningCycleRepository planningCycles;
    private final DemandAttachmentRepository attachments;
    private final DemandHistoryRepository history;
    private final DemandParticipatingDirectionRepository participatingDirections;
    private final DemandCodeService codeService;
    private final DemandHistoryService historyService;
    private final CommitteeService committees;
    private final CommitteeSuggestionService committeeSuggestions;
    private final ProjectService projects;
    private final ProgramService programs;
    private final OperationalPlanService operationalPlans;
    private final LookupValueService lookups;
    private final DemandScoringService scoringService;
    private final DemandScoreLifecycleService scoreLifecycle;
    private final DemandPreScoreService preScoreService;
    private final DemandProfileRequirementService profileRequirements;
    private final SupplierRepository supplierRepository;
    private final DemandPlanningCycleService planningCycleService;

    public DemandService(DemandRepository demands, DemandPlanningCycleRepository planningCycles,
            DemandAttachmentRepository attachments, DemandHistoryRepository history,
            DemandParticipatingDirectionRepository participatingDirections,
            DemandCodeService codeService, DemandHistoryService historyService,
            CommitteeService committees, CommitteeSuggestionService committeeSuggestions,
            ProjectService projects, ProgramService programs, OperationalPlanService operationalPlans,
            LookupValueService lookups,
            DemandScoringService scoringService, DemandScoreLifecycleService scoreLifecycle,
            DemandPreScoreService preScoreService, DemandProfileRequirementService profileRequirements,
            SupplierRepository supplierRepository, DemandPlanningCycleService planningCycleService) {
        this.demands = demands;
        this.planningCycles = planningCycles;
        this.attachments = attachments;
        this.history = history;
        this.participatingDirections = participatingDirections;
        this.codeService = codeService;
        this.historyService = historyService;
        this.committees = committees;
        this.committeeSuggestions = committeeSuggestions;
        this.projects = projects;
        this.programs = programs;
        this.operationalPlans = operationalPlans;
        this.lookups = lookups;
        this.scoringService = scoringService;
        this.scoreLifecycle = scoreLifecycle;
        this.preScoreService = preScoreService;
        this.profileRequirements = profileRequirements;
        this.supplierRepository = supplierRepository;
        this.planningCycleService = planningCycleService;
    }

    public DemandResponse create(Create req, String actorId) {
        Demand demand = new Demand();
        demand.setCode(codeService.nextCode());
        demand.setCreatedBy(actor(actorId));
        demand.setUpdatedBy(actor(actorId));
        apply(req, demand);
        Demand saved = demands.save(demand);
        DemandPlanningCycle cycle = planningCycleService.createInitialCycle(saved, actor(actorId));
        if (req.attachments() != null) {
            req.attachments().forEach(a -> addAttachment(saved, new AttachmentCreate(a.name(), a.url(), a.contentType()),
                    actorId));
        }
        if (req.participatingDirections() != null && !req.participatingDirections().isEmpty()) {
            req.participatingDirections().forEach(pd -> {
                DemandParticipatingDirection direction = new DemandParticipatingDirection();
                direction.setDemand(saved);
                direction.setDirectionName(pd.directionName());
                direction.setDirectionCode(pd.directionCode());
                direction.setAreaName(pd.areaName());
                direction.setAreaCode(pd.areaCode());
                direction.setParticipationType(pd.participationType());
                direction.setObservations(pd.observations());
                direction.setCreatedBy(actor(actorId));
                direction.setUpdatedBy(actor(actorId));
                participatingDirections.save(direction);
            });
        }
        profileRequirements.sync(saved, req.profileRequirements());
        historyService.log(saved, "CREATED", null, cycle.getStatus().name(), "Demanda criada", actor(actorId),
                actor(actorId), Map.of("code", saved.getCode()));
        return map(saved);
    }

    @Transactional
    public DemandResponse update(UUID id, Update req, String actorId) {
        Demand demand = entity(id);
        ScoreRelevantState previousScoreInputs = scoreRelevantState(demand);
        ComplexityRelevantState previousComplexityInputs = complexityRelevantState(demand);
        LocalDate previousDesiredDate = demand.getDesiredDate();
        apply(toCreate(req), demand);
        handleScoreRelevantChanges(demand, previousScoreInputs);
        invalidateComplexityIfNeeded(demand, previousComplexityInputs,
                req.participatingDirections() != null || req.profileRequirements() != null);
        refreshPlannedStartDateIfNeeded(demand, previousDesiredDate);
        demand.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(demand);

        if (req.participatingDirections() != null) {
            participatingDirections.deleteByDemandId(id);
            req.participatingDirections().forEach(pd -> {
                DemandParticipatingDirection direction = new DemandParticipatingDirection();
                direction.setDemand(saved);
                direction.setDirectionName(pd.directionName());
                direction.setDirectionCode(pd.directionCode());
                direction.setAreaName(pd.areaName());
                direction.setAreaCode(pd.areaCode());
                direction.setParticipationType(pd.participationType());
                direction.setObservations(pd.observations());
                direction.setCreatedBy(actor(actorId));
                direction.setUpdatedBy(actor(actorId));
                participatingDirections.save(direction);
            });
        }
        profileRequirements.sync(saved, req.profileRequirements());

        historyService.log(saved, "UPDATED", null, null, "Demanda atualizada", actor(actorId), actor(actorId), Map.of());
        return map(saved);
    }

    @Transactional
    public DemandResponse patch(UUID id, Patch req, String actorId) {
        Demand d = entity(id);
        ScoreRelevantState previousScoreInputs = scoreRelevantState(d);
        ComplexityRelevantState previousComplexityInputs = complexityRelevantState(d);
        LocalDate previousDesiredDate = d.getDesiredDate();
        Create merged = mergePatch(d, req);
        apply(merged, d);
        handleScoreRelevantChanges(d, previousScoreInputs);
        invalidateComplexityIfNeeded(d, previousComplexityInputs,
                req.participatingDirections() != null || req.profileRequirements() != null);
        refreshPlannedStartDateIfNeeded(d, previousDesiredDate);
        d.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(d);

        if (req.participatingDirections() != null) {
            participatingDirections.deleteByDemandId(id);
            req.participatingDirections().forEach(pd -> {
                DemandParticipatingDirection direction = new DemandParticipatingDirection();
                direction.setDemand(saved);
                direction.setDirectionName(pd.directionName());
                direction.setDirectionCode(pd.directionCode());
                direction.setAreaName(pd.areaName());
                direction.setAreaCode(pd.areaCode());
                direction.setParticipationType(pd.participationType());
                direction.setObservations(pd.observations());
                direction.setCreatedBy(actor(actorId));
                direction.setUpdatedBy(actor(actorId));
                participatingDirections.save(direction);
            });
        }
        profileRequirements.sync(saved, req.profileRequirements());

        historyService.log(saved, "UPDATED", null, null, "Demanda atualizada parcialmente", actor(actorId),
                actor(actorId), Map.of());
        return map(saved);
    }

    @Transactional
    public void delete(UUID id, String actorId) {
        Demand d = entity(id);
        List<DemandPlanningCycle> openCycles = planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(id).stream()
                .filter(c -> c.getStatus() != CLOSED)
                .toList();
        for (DemandPlanningCycle cycle : openCycles) {
            planningCycleService.transitionStatus(cycle, CLOSED, "Demanda arquivada por eliminação lógica", actorId);
        }
        d.setDeletedAt(Instant.now());
        d.setUpdatedBy(actor(actorId));
        historyService.log(d, "ARCHIVED", null, "CLOSED", "Demanda arquivada por eliminação lógica",
                actor(actorId), actor(actorId), Map.of());
    }

    @Transactional
    public DemandResponse changeStatus(UUID id, UUID strategicPlanId, DemandPlanningCycleDtos.TransitionRequest req,
            String actorId) {
        Demand d = entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        planningCycleService.transitionStatus(cycle, req.status(), req.reason(), actor(actorId));
        return map(d);
    }

    @Transactional
    public DemandResponse sendToStrategicCommittee(UUID id, UUID strategicPlanId, String reason, String actorId) {
        Demand d = entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        planningCycleService.sendToStrategicCommittee(cycle, reason, actor(actorId));
        return map(d);
    }

    @Transactional
    public List<DemandResponse> sendToStrategicCommitteeBulk(List<UUID> demandIds, UUID strategicPlanId, String reason,
            String actorId) {
        return demandIds.stream()
                .distinct()
                .map(demandId -> sendToStrategicCommittee(demandId, strategicPlanId, reason, actorId))
                .toList();
    }

    public CommitteeSuggestionResponse committeeSuggestion(UUID id, UUID strategicPlanId) {
        Demand demand = entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        return committeeSuggestions.suggest(demand, cycle);
    }

    @Transactional
    public List<DemandResponse> listInPriorizationByUsername(String username, UUID strategicPlanId) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("username é obrigatório");
        }

        List<Committee> userCommittees = committees.findByMemberCode(username.trim());
        if (userCommittees.isEmpty()) {
            throw domain(HttpStatus.NOT_FOUND, "COMMITTEE_NOT_FOUND_FOR_USER",
                    "Nenhum comité encontrado para o username informado", Map.of("username", username));
        }

        List<UUID> committeeIds = userCommittees.stream()
            .map(Committee::getId)
            .toList();

        Specification<DemandPlanningCycle> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                root.get("responsibleCommittee").get("id").in(committeeIds),
                cb.equal(root.get("status"), PRIORITIZATION));
        return planningCycles.findAll(spec, Sort.by(Sort.Direction.DESC, "preScore")).stream()
                .map(cycle -> map(cycle.getDemand()))
                .toList();
    }

    @Transactional
    public DemandResponse assignCommittee(UUID id, UUID strategicPlanId, AssignCommitteeRequest request, String actorId) {
        Demand demand = entity(id);
        Committee committee = committees.findActiveEntityById(request.committeeId());
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        planningCycleService.assignCommittee(cycle, committee, actor(actorId));
        return map(demand);
    }

    @Transactional
    public PreScoreResponse calculatePreScore(UUID id, UUID strategicPlanId, String actorId) {
        Demand demand = entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        DemandPreScoreService.PreScoreResult result = preScoreService.calculate(demand, cycle);
        planningCycles.save(cycle);
        historyService.log(demand, "PRE_SCORE_CALCULATED", cycle.getStatus().name(), cycle.getStatus().name(),
                "Pré-score calculado", actor(actorId), actor(actorId),
                Map.of("preScore", result.preScore().toString(), "classification", result.preScoreClassification()));
        return new PreScoreResponse(result.preScore(), result.preScoreClassification());
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
    public ConvertResponse convertToProject(UUID demandId, UUID strategicPlanId, ConvertToProject req, String actorId) {
        Demand d = entity(demandId);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(demandId, strategicPlanId);

        Program program = req.programId() != null ? programs.findById(req.programId())
                : Optional.ofNullable(cycle.getProgram())
                        .orElseThrow(() -> new BadRequestException("programId é obrigatório para conversão"));
        OperationalPlan operationalPlan = req.operationalPlanId() != null
                ? operationalPlans.findById(req.operationalPlanId())
                : cycle.getOperationalPlan();
        validateConversionOperationalPlan(cycle, operationalPlan);
        LookupValue domain = resolveConversionDomain(d, req.domainId());
        LookupValue projectType = req.projectTypeId() == null ? null
                : lookups.requireActiveInCategory(req.projectTypeId(), "PROJECT_TYPE");
        LookupValue projectPhase = req.projectPhaseId() == null ? null
                : lookups.requireActiveInCategory(req.projectPhaseId(), "PROJECT_PHASE");
        Supplier mainSupplier = resolveMainSupplier(program, req.mainSupplierId());

        String projectName = firstNonBlank(req.projectName(), d.getTitle());
        String businessArea = firstNonBlank(req.businessArea(), d.getAreaCode());
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
        Integer ranking = req.ranking() != null ? req.ranking() : cycle.getPortfolioRank();
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
            ? req.responsibleDirection() : d.getDirectionName());
        p.setDirectionCode(d.getDirectionCode());
        p.setDirectionName(firstNonBlank(d.getDirectionName(), d.getDirectionCode()));
        p.setAreaCode(d.getAreaCode());
        p.setAreaName(firstNonBlank(d.getAreaName(), d.getAreaCode()));
        p.setResponsibleTeam(req.responsibleTeam());
        p.setProjectManager(projectManager);
        p.setProjectManagerId(req.managerId());
        p.setStatus(ProjectStatus.PLANNED);
        p.setProjectPhase(projectPhase);
        p.setMainSupplier(mainSupplier);
        if (mainSupplier != null) {
            p.getSuppliers().add(mainSupplier);
        }
        p.setImpactedSystem(req.impactedSystem() != null ? req.impactedSystem() : d.getImpactedSystem());
        p.setExpectedBenefits(req.expectedBenefits() != null ? req.expectedBenefits() : d.getExpectedBenefit());
        p.setPriority(req.priority() != null ? req.priority() : mapPriority(d.getInitialPriority()));
        p.setExecutionRank(ranking);
        p.setBudgetLine(firstNonBlank(req.budgetLine(), domain != null ? domain.getCode() : null));
        p.setBudget(budget);
        p.setPlanType(req.planType());
        Project savedProject = projects.saveFromDemand(p, d, cycle, plannedStartDate, req.startDate(), plannedEndDate,
                req.endDate(), req.delayReasons(), actor(actorId));
        DemandPlanningCycle convertedCycle = planningCycleService.convertToProject(cycle, savedProject, actor(actorId));

        return new ConvertResponse(
                new DemandConvertInfo(d.getId(), d.getCode(), convertedCycle.getStatus(),
                        savedProject.getId(), convertedCycle.getScoreStatus(), convertedCycle.getScoreCalculatedAt(),
                        convertedCycle.getScoreInvalidatedAt(), convertedCycle.getScoreInvalidationReason(),
                        scoreLifecycle.previousScoreSnapshot(convertedCycle)),
                mapProjectConvertInfo(savedProject));
    }

    @Transactional
    public PagedDemandsResponse listPortfolioRanked(int page, int size, UUID strategicPlanId, String directionCode,
            String areaCode) {
        Specification<DemandPlanningCycle> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                cb.isNotNull(root.get("portfolioRank")));
        if (directionCode != null && !directionCode.isBlank())
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("demand").get("directionCode")),
                    directionCode.trim().toUpperCase(Locale.ROOT)));
        if (areaCode != null && !areaCode.isBlank())
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("demand").get("areaCode")),
                    areaCode.trim().toUpperCase(Locale.ROOT)));
        Page<DemandPlanningCycle> p = planningCycles.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "portfolioRank")));
        return new PagedDemandsResponse(p.getContent().stream().map(c -> map(c.getDemand())).toList(), page, size,
                p.getTotalElements(), p.getTotalPages());
    }

    @Transactional
    public PagedDemandsResponse list(int page, int size, String sort, String search, List<DemandPlanningStatus> status,
            String type, String origin, String initialPriority, String urgency, UUID strategicPlanId,
            UUID operationalPlanId, UUID strategicPillarId, UUID strategicObjectiveId, UUID programId,
            String requester, String businessArea, LocalDate createdFrom, LocalDate createdTo) {
        String sortField = sort == null || sort.isBlank() ? "createdAt" : sort;
        Specification<Demand> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("requester")), like),
                    cb.like(cb.lower(root.get("areaName")), like),
                    cb.like(cb.lower(root.get("directionName")), like),
                    cb.like(cb.lower(root.get("areaCode")), like),
                    cb.like(cb.lower(root.get("directionCode")), like),
                    cb.like(cb.lower(root.get("easyVistaRef")), like)));
        }
        if (type != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("type").get("code")), norm(type)));
        if (origin != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("origin")), norm(origin)));
        if (initialPriority != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("initialPriority")), norm(initialPriority)));
        if (urgency != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("urgency")), norm(urgency)));
        if (requester != null)
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("requester")), "%" + requester.toLowerCase() + "%"));
        if (businessArea != null)
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("areaName")), "%" + businessArea.toLowerCase() + "%"));
        if (createdFrom != null) {
            Instant from = createdFrom.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        }
        if (createdTo != null) {
            Instant to = createdTo.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("createdAt"), to));
        }

        boolean cycleFiltered = strategicPlanId != null || operationalPlanId != null || strategicPillarId != null
                || strategicObjectiveId != null || programId != null || (status != null && !status.isEmpty());
        if (cycleFiltered) {
            Specification<DemandPlanningCycle> cycleSpec = (root, query, cb) -> cb.conjunction();
            if (strategicPlanId != null)
                cycleSpec = cycleSpec.and((r, q, cb) -> cb.equal(r.get("strategicPlan").get("id"), strategicPlanId));
            if (operationalPlanId != null)
                cycleSpec = cycleSpec.and((r, q, cb) -> cb.equal(r.get("operationalPlan").get("id"), operationalPlanId));
            if (strategicPillarId != null)
                cycleSpec = cycleSpec.and((r, q, cb) -> cb.equal(r.get("strategicPillar").get("id"), strategicPillarId));
            if (strategicObjectiveId != null)
                cycleSpec = cycleSpec
                        .and((r, q, cb) -> cb.equal(r.get("strategicObjective").get("id"), strategicObjectiveId));
            if (programId != null)
                cycleSpec = cycleSpec.and((r, q, cb) -> cb.equal(r.get("program").get("id"), programId));
            if (status != null && !status.isEmpty())
                cycleSpec = cycleSpec.and((r, q, cb) -> r.get("status").in(status));
            Set<UUID> matchingDemandIds = planningCycles.findAll(cycleSpec).stream()
                    .map(c -> c.getDemand().getId())
                    .collect(java.util.stream.Collectors.toSet());
            if (matchingDemandIds.isEmpty()) {
                return new PagedDemandsResponse(List.of(), page, size, 0, 0);
            }
            spec = spec.and((r, q, cb) -> r.get("id").in(matchingDemandIds));
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

    @Transactional
    public DemandPlanningCycleDtos.Response getCycle(UUID demandId, UUID strategicPlanId) {
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(demandId, strategicPlanId);
        return mapCycle(cycle);
    }

    @Transactional
    public DemandPlanningCycleDtos.Response updateCycleEnrichment(UUID demandId, UUID strategicPlanId,
            DemandPlanningCycleDtos.EnrichmentRequest req, String actorId) {
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(demandId, strategicPlanId);
        DemandPlanningCycle saved = planningCycleService.updateEnrichment(cycle, req.strategicPillarId(),
                req.strategicObjectiveId(), req.programId(), req.capacityStatus(), req.riskStatus(), actor(actorId));
        return mapCycle(saved);
    }

    private DemandPlanningCycleDtos.Response mapCycle(DemandPlanningCycle c) {
        return new DemandPlanningCycleDtos.Response(
                c.getId(), c.getDemand().getId(),
                mapStrategicPlan(c.getStrategicPlan()), mapOperationalPlan(c.getOperationalPlan()),
                mapStrategicPillar(c.getStrategicPillar()), mapStrategicObjective(c.getStrategicObjective()),
                mapProgram(c.getProgram()), c.getStatus(), mapCommittee(c.getResponsibleCommittee()),
                c.isInStrategicCommittee(), c.getStrategicCommitteeAt(), c.getPreScore(), c.getPreScoreClassification(),
                c.getScoreTotal(), c.getScoreStatus(), c.getScoreCalculatedAt(), c.getScoreInvalidatedAt(),
                c.getScoreInvalidationReason(), scoreLifecycle.previousScoreSnapshot(c), c.getPortfolioRank(),
                c.getDirectionRank(), c.getCommitteeRank(), c.getCapacityStatus(), c.getRiskStatus(),
                c.getApprovalType(), c.getCommitteeDecision(), c.getRejectionReason(), c.getDecisionAt(),
                c.getDecisionBy(), c.getReprioritizationReason(), c.getReprioritizationJustification(),
                c.getReprioritizedAt(), c.getReprioritizedBy(), c.isCarriedOver(),
                c.getPreviousCycle() != null ? c.getPreviousCycle().getId() : null, c.getCycleEntryAt(),
                c.getConvertedProject() != null ? c.getConvertedProject().getId() : null,
                c.getConvertedProgram() != null ? c.getConvertedProgram().getId() : null, c.getConvertedAt(),
                c.getConvertedBy(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
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

    @Transactional
    public List<DemandPlanningCycleDtos.PlanningHistoryItem> planningHistory(UUID id) {
        entity(id);
        return planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(id).stream()
                .map(c -> new DemandPlanningCycleDtos.PlanningHistoryItem(
                        c.getStrategicPlan().getStartYear(), c.getStrategicPlan().getName(),
                        c.getOperationalPlan().getName(), c.getStatus(), c.getScoreTotal(), c.getPortfolioRank(),
                        c.getCommitteeDecision(), c.isCarriedOver()))
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

    private void apply(Create req, Demand d) {
        String normalizedOrigin = req.origin() == null || req.origin().isBlank() ? "MANUAL" : norm(req.origin());
        d.setTitle(req.title());
        d.setDescription(req.description());
        d.setRequester(req.requester());
        d.setAreaName(req.areaName());
        d.setAreaCode(req.areaCode());
        d.setDirectionName(req.directionName());
        d.setDirectionCode(req.directionCode());
        d.setDirectionParticipationType(req.directionParticipationType());
        d.setSponsor(req.sponsor());
        d.setType(req.typeId() == null ? null : lookups.requireActiveInCategory(req.typeId(), "DEMAND_TYPE"));
        d.setOrigin(normalizedOrigin);
        d.setEasyVistaRef(req.easyVistaRef());
        syncSuppliers(d, req.supplierIds());
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
        d.setRisksIdentified(req.risksIdentified());
        d.setDependenciesIdentified(req.dependenciesIdentified());
        if (d.getId() == null) {
            d.setDependenciesCount(0);
        }

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
    }

    private void validateLookupOrEnum(String category, String code, Set<String> fallback) {
        if (code == null || code.isBlank())
            return;
        boolean valid = lookups.existsCode(category, code) || (fallback != null && fallback.contains(norm(code)));
        if (!valid)
            throw new BadRequestException("Valor inválido para " + category + ": " + code);
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
        List<ParticipatingDirectionResponse> participatingDirs = participatingDirections
                .findByDemandIdOrderByCreatedAtAsc(d.getId()).stream()
                .map(this::mapParticipatingDirection)
                .toList();
        List<DemandProfileRequirementResponse> profileRequirementResponses = profileRequirements.list(d.getId());
        String typeCode = d.getType() != null ? d.getType().getCode() : null;
        String domainCode = d.getDomain() != null ? d.getDomain().getCode() : null;
        LookupValueSummary typeData = mapLookupValue(d.getType());
        LookupValueSummary domainData = mapLookupValue(d.getDomain());
        CurrentCycleSummary currentCycle = planningCycleService.getCurrentPlanningCycle(d.getId())
                .map(this::mapCurrentCycle).orElse(null);
        List<SupplierSummary> supplierSummaries = d.getSuppliers().stream()
                .sorted(Comparator.comparing(Supplier::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::mapSupplier)
                .toList();
        return new DemandResponse(d.getId(), d.getCode(), d.getTitle(), d.getDescription(), d.getRequester(), d.getAreaName(),
            d.getAreaCode(), d.getDirectionName(), d.getDirectionCode(), d.getDirectionParticipationType(), d.getSponsor(), d.getTypeId(), typeCode, d.getOrigin(), d.getEasyVistaRef(),
                d.getDomainId(), domainCode, d.getImpactedSystem(),
                d.getInitialPriority(), d.getEstimatedEffort(),
                d.getExpectedImpact(),
                d.getExpectedBenefit(), d.getUrgency(), d.getEstimatedBudget(), d.getDesiredDate(), d.getNotes(),
                d.getRisksIdentified(),
                d.getDependenciesIdentified(), d.getDirectionsCount(), d.getProfilesCount(), d.getTotalResources(),
                d.getDependenciesCount(), d.getComplexityScore(), d.getComplexity(), d.getEstimatedDurationMonths(),
                d.getPlannedStartDate(),
                d.getCreatedAt(), d.getCreatedBy(), d.getUpdatedAt(), d.getUpdatedBy(),
                d.getVersion(), typeData, domainData, currentCycle,
                att, participatingDirs, profileRequirementResponses,
                supplierSummaries);
    }

    private CurrentCycleSummary mapCurrentCycle(DemandPlanningCycle c) {
        return new CurrentCycleSummary(c.getId(), mapStrategicPlan(c.getStrategicPlan()),
                mapOperationalPlan(c.getOperationalPlan()), c.getStatus(), c.getScoreTotal(), c.getPortfolioRank(),
                c.getCommitteeDecision(), c.isCarriedOver());
    }

    private SupplierSummary mapSupplier(Supplier supplier) {
        var currency = supplier.getCurrency();
        CurrencySummary currencySummary = currency == null ? null
                : new CurrencySummary(currency.getId(), currency.getCode(), currency.getName(), currency.getSymbol());
        return new SupplierSummary(supplier.getId(), supplier.getName(), supplier.getCountry(),
                supplier.getRatePerHour(), supplier.getRateUnit(), currencySummary);
    }

    private void syncSuppliers(Demand demand, Set<UUID> supplierIds) {
        if (supplierIds == null) {
            return;
        }

        Set<UUID> requestedIds = new HashSet<>(supplierIds);
        if (requestedIds.remove(null)) {
            throw new BadRequestException("supplierIds não pode conter valores nulos");
        }

        List<Supplier> resolvedSuppliers = supplierRepository.findAllById(requestedIds);
        Set<UUID> resolvedIds = new HashSet<>();
        resolvedSuppliers.forEach(supplier -> resolvedIds.add(supplier.getId()));

        requestedIds.removeAll(resolvedIds);
        if (!requestedIds.isEmpty()) {
            throw new ResourceNotFoundException("Fornecedor(es) não encontrado(s): " + requestedIds);
        }

        demand.getSuppliers().clear();
        demand.getSuppliers().addAll(resolvedSuppliers);
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

    private void validateConversionOperationalPlan(DemandPlanningCycle cycle, OperationalPlan operationalPlan) {
        if (operationalPlan == null || cycle.getStrategicPlan() == null) {
            return;
        }
        StrategicPlan plan = operationalPlan.getStrategicPlan();
        if (plan == null || !Objects.equals(plan.getId(), cycle.getStrategicPlan().getId())) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "OPERATIONAL_PLAN_STRATEGIC_PLAN_MISMATCH",
                    "O plano operacional do projeto deve pertencer ao plano estratégico do ciclo da demanda",
                    Map.of("operationalPlanId", operationalPlan.getId().toString(),
                            "strategicPlanId", cycle.getStrategicPlan().getId().toString()));
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
                project.getExecution() != null ? project.getExecution().getPlannedStartDate() : null,
                project.getExecution() != null ? project.getExecution().getActualStartDate() : null,
                project.getExecution() != null ? project.getExecution().getPlannedEndDate() : null,
                project.getExecution() != null ? project.getExecution().getActualEndDate() : null,
                project.getPriority(),
                project.getExecutionRank(),
                project.getBudgetLine(),
                project.getBudget(),
                project.getPlanType(),
                project.getExecution() != null ? project.getExecution().getDelayReasons() : null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void recomputeRanksIfNeeded(BigDecimal previousScore, String previousDirection, DemandPlanningCycle cycle) {
        boolean leftRanking = previousScore != null && cycle.getScoreTotal() == null;
        boolean changedDirection = previousScore != null && cycle.getScoreTotal() != null
                && !Objects.equals(normalizeDirection(previousDirection),
                        normalizeDirection(cycle.getDemand().getDirectionCode()));
        if (leftRanking || changedDirection) {
            scoringService.recomputeRanks(cycle.getStrategicPlan().getId());
        }
    }

    private String normalizeDirection(String direction) {
        return direction == null || direction.isBlank() ? null : direction.trim().toUpperCase(Locale.ROOT);
    }

    private void handleScoreRelevantChanges(Demand demand, ScoreRelevantState previousState) {
        if (previousState.equals(scoreRelevantState(demand))) {
            return;
        }
        List<DemandPlanningCycle> openCycles = planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(demand.getId())
                .stream()
                .filter(c -> c.getStatus() != APPROVED && c.getStatus() != CONDITIONALLY_APPROVED
                        && c.getStatus() != CONVERTED_TO_PROJECT && c.getStatus() != CONVERTED_TO_PROGRAM
                        && c.getStatus() != CLOSED)
                .toList();
        for (DemandPlanningCycle cycle : openCycles) {
            String previousDirection = cycle.getDemand().getDirectionCode();
            BigDecimal previousScore = cycle.getScoreTotal();
            if (scoreLifecycle.isValid(cycle)) {
                scoreLifecycle.invalidateAfterRelevantUpdate(cycle);
                planningCycles.save(cycle);
                recomputeRanksIfNeeded(previousScore, previousDirection, cycle);
            }
        }
    }

    private ScoreRelevantState scoreRelevantState(Demand demand) {
        return new ScoreRelevantState(
                demand.getDescription(),
                demand.getTypeId(),
                demand.getDomainId(),
                demand.getImpactedSystem(),
                demand.getInitialPriority(),
                demand.getEstimatedEffort(),
                demand.getExpectedImpact(),
                demand.getExpectedBenefit(),
                demand.getUrgency(),
                normalizedDecimal(demand.getEstimatedBudget()),
                demand.getDesiredDate(),
                demand.getRisksIdentified(),
                demand.getDependenciesIdentified());
    }

    private BigDecimal normalizedDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private ComplexityRelevantState complexityRelevantState(Demand demand) {
        return new ComplexityRelevantState(demand.getDirectionCode(), demand.getDirectionName(),
                demand.getProfilesCount(), demand.getTotalResources(), demand.getDependenciesCount());
    }

    private void invalidateComplexityIfNeeded(Demand demand, ComplexityRelevantState previousState,
            boolean participatingDirectionsChanged) {
        if (participatingDirectionsChanged || !previousState.equals(complexityRelevantState(demand))) {
            invalidateComplexity(demand);
        }
    }

    private void invalidateComplexity(Demand demand) {
        demand.setDirectionsCount(null);
        demand.setComplexityScore(null);
        demand.setComplexity(null);
        demand.setEstimatedDurationMonths(null);
        demand.setPlannedStartDate(null);
    }

    private void refreshPlannedStartDateIfNeeded(Demand demand, LocalDate previousDesiredDate) {
        if (!Objects.equals(previousDesiredDate, demand.getDesiredDate())
                && demand.getEstimatedDurationMonths() != null) {
            demand.setPlannedStartDate(demand.getDesiredDate() == null ? null
                    : demand.getDesiredDate().minusMonths(demand.getEstimatedDurationMonths()));
        }
    }

    private record ComplexityRelevantState(
            String directionCode,
            String directionName,
            Integer profilesCount,
            Integer totalResources,
            Integer dependenciesCount) {
    }

    private record ScoreRelevantState(
            String description,
            UUID typeId,
            UUID domainId,
            String impactedSystem,
            String initialPriority,
            String estimatedEffort,
            String expectedImpact,
            String expectedBenefit,
            String urgency,
            BigDecimal estimatedBudget,
            LocalDate desiredDate,
            String risksIdentified,
            String dependenciesIdentified) {
    }

    private Create mergePatch(Demand d, Patch p) {
        return new Create(
                p.title() != null ? p.title() : d.getTitle(),
                p.description() != null ? p.description() : d.getDescription(),
                p.requester() != null ? p.requester() : d.getRequester(),
                p.areaName() != null ? p.areaName() : d.getAreaName(),
                p.areaCode() != null ? p.areaCode() : d.getAreaCode(),
                p.directionName() != null ? p.directionName() : d.getDirectionName(),
                p.directionCode() != null ? p.directionCode() : d.getDirectionCode(),
                p.directionParticipationType() != null ? p.directionParticipationType() : d.getDirectionParticipationType(),
                p.sponsor() != null ? p.sponsor() : d.getSponsor(),
                p.typeId() != null ? p.typeId() : d.getTypeId(),
                p.origin() != null ? p.origin() : d.getOrigin(),
                p.easyVistaRef() != null ? p.easyVistaRef() : d.getEasyVistaRef(),
                p.supplierIds(),
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
                p.risksIdentified() != null ? p.risksIdentified() : d.getRisksIdentified(),
                p.dependenciesIdentified() != null ? p.dependenciesIdentified() : d.getDependenciesIdentified(),
                p.dependenciesCount() != null ? p.dependenciesCount() : d.getDependenciesCount(),
                null,
                p.participatingDirections(),
                p.profileRequirements());
    }

    private Create toCreate(Update u) {
        return new Create(u.title(), u.description(), u.requester(), u.areaName(), u.areaCode(), u.directionName(), u.directionCode(), u.directionParticipationType(), u.sponsor(), u.typeId(),
                u.origin(), u.easyVistaRef(), u.supplierIds(), u.domainId(), u.impactedSystem(), u.initialPriority(),
                u.estimatedEffort(), u.expectedImpact(), u.expectedBenefit(), u.urgency(), u.estimatedBudget(),
                u.desiredDate(), u.notes(), u.risksIdentified(),
                u.dependenciesIdentified(), u.dependenciesCount(),
                null, u.participatingDirections(), u.profileRequirements());
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
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String actor(String value) {
        return value == null || value.isBlank() ? "system" : value;
    }

    private DomainException domain(HttpStatus status, String code, String message, Map<String, Object> details) {
        return new DomainException(status, code, message, details);
    }

    @Transactional
    public ReprioritizePortfolioRankResponse reprioritizePortfolioRank(UUID id, UUID strategicPlanId,
            ReprioritizePortfolioRankRequest req, String actorId) {
        Demand demand = entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);

        if (cycle.getStatus() != APPROVED && cycle.getStatus() != CONDITIONALLY_APPROVED) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_NOT_APPROVED_FOR_REPRIORITIZATION",
                    "Apenas demandas aprovadas ou aprovadas condicionalmente podem ser repriorizadas",
                    Map.of("status", cycle.getStatus().name()));
        }

        Specification<DemandPlanningCycle> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                cb.isNull(root.get("convertedProject")),
                cb.isNull(root.get("convertedProgram")),
                cb.or(
                        cb.equal(root.get("status"), APPROVED),
                        cb.equal(root.get("status"), CONDITIONALLY_APPROVED)),
                cb.isNotNull(root.get("portfolioRank")));
        List<DemandPlanningCycle> approvedCycles = new ArrayList<>(planningCycles.findAll(spec));

        approvedCycles.sort(Comparator.comparingInt(c -> c.getPortfolioRank() != null ? c.getPortfolioRank() : Integer.MAX_VALUE));

        int newPosition = req.newPosition();
        if (newPosition < 1 || newPosition > approvedCycles.size()) {
            throw new BadRequestException("newPosition deve estar entre 1 e " + approvedCycles.size());
        }

        Integer currentRank = cycle.getPortfolioRank();
        if (currentRank != null && currentRank == newPosition) {
            throw new BadRequestException("newPosition não pode ser igual ao portfolioRank atual");
        }

        Map<UUID, Integer> previousRanks = new HashMap<>();
        for (DemandPlanningCycle c : approvedCycles) {
            previousRanks.put(c.getId(), c.getPortfolioRank());
        }

        approvedCycles.removeIf(c -> c.getId().equals(cycle.getId()));
        approvedCycles.add(newPosition - 1, cycle);

        for (int i = 0; i < approvedCycles.size(); i++) {
            approvedCycles.get(i).setPortfolioRank(i + 1);
        }

        cycle.setReprioritizationReason(req.reprioritizationReason());
        cycle.setReprioritizationJustification(req.reprioritizationJustification());
        cycle.setReprioritizedAt(Instant.now());
        cycle.setReprioritizedBy(actor(actorId));

        planningCycles.saveAll(approvedCycles);

        historyService.log(demand, "PORTFOLIO_RANK_CHANGED", cycle.getStatus().name(), cycle.getStatus().name(),
                "Ranking repriorizado: " + previousRanks.get(cycle.getId()) + " → " + newPosition,
                actor(actorId), actor(actorId),
                Map.of("previousRank", String.valueOf(previousRanks.get(cycle.getId())), "newRank", String.valueOf(newPosition),
                       "reason", req.reprioritizationReason()));

        List<RankedDemandInfo> affectedDemands = approvedCycles.stream()
                .filter(c -> !c.getId().equals(cycle.getId()))
                .map(c -> new RankedDemandInfo(c.getDemand().getId(), c.getDemand().getCode(), c.getDemand().getTitle(),
                        previousRanks.get(c.getId()), c.getPortfolioRank()))
                .toList();

        return new ReprioritizePortfolioRankResponse(
                demand.getId(),
                demand.getCode(),
                demand.getTitle(),
                previousRanks.get(cycle.getId()),
                cycle.getPortfolioRank(),
                cycle.getStatus(),
                req.reprioritizationReason(),
                req.reprioritizationJustification(),
                cycle.getReprioritizedAt(),
                cycle.getReprioritizedBy(),
                affectedDemands
        );
    }

    @Transactional
    public List<ParticipatingDirectionResponse> listParticipatingDirections(UUID demandId) {
        entity(demandId);
        return participatingDirections.findByDemandIdOrderByCreatedAtAsc(demandId).stream()
                .map(this::mapParticipatingDirection)
                .toList();
    }

    @Transactional
    public ParticipatingDirectionResponse addParticipatingDirection(UUID demandId, ParticipatingDirectionCreate req, String actorId) {
        Demand demand = entity(demandId);

        DemandParticipatingDirection pd = new DemandParticipatingDirection();
        pd.setDemand(demand);
        pd.setDirectionName(req.directionName());
        pd.setDirectionCode(req.directionCode());
        pd.setAreaName(req.areaName());
        pd.setAreaCode(req.areaCode());
        pd.setParticipationType(req.participationType());
        pd.setObservations(req.observations());
        pd.setCreatedBy(actor(actorId));
        pd.setUpdatedBy(actor(actorId));

        DemandParticipatingDirection saved = participatingDirections.save(pd);
        invalidateComplexity(demand);
        demand.setUpdatedBy(actor(actorId));
        demands.save(demand);

        historyService.log(demand, "PARTICIPATING_DIRECTION_ADDED", null, null,
            "Direção participante adicionada: " + req.directionName(),
                actor(actorId), actor(actorId),
            Map.of("directionName", req.directionName(), "directionCode", req.directionCode(), "participationType", req.participationType().name()));

        return mapParticipatingDirection(saved);
    }

    @Transactional
    public ParticipatingDirectionResponse updateParticipatingDirection(UUID demandId, UUID directionId,
            ParticipatingDirectionUpdate req, String actorId) {
        Demand demand = entity(demandId);
        DemandParticipatingDirection pd = participatingDirections.findById(directionId)
                .orElseThrow(() -> new ResourceNotFoundException("Direção participante não encontrada: " + directionId));

        if (!pd.getDemand().getId().equals(demandId)) {
            throw new BadRequestException("Direção participante não pertence à demanda informada");
        }

        pd.setDirectionName(req.directionName());
        pd.setDirectionCode(req.directionCode());
        pd.setAreaName(req.areaName());
        pd.setAreaCode(req.areaCode());
        pd.setParticipationType(req.participationType());
        pd.setObservations(req.observations());
        pd.setUpdatedBy(actor(actorId));

        DemandParticipatingDirection saved = participatingDirections.save(pd);
        invalidateComplexity(demand);
        demand.setUpdatedBy(actor(actorId));
        demands.save(demand);

        historyService.log(demand, "PARTICIPATING_DIRECTION_UPDATED", null, null,
            "Direção participante atualizada: " + req.directionName(),
                actor(actorId), actor(actorId),
                Map.of("directionId", directionId.toString()));

        return mapParticipatingDirection(saved);
    }

    @Transactional
    public void deleteParticipatingDirection(UUID demandId, UUID directionId, String actorId) {
        Demand demand = entity(demandId);
        DemandParticipatingDirection pd = participatingDirections.findById(directionId)
                .orElseThrow(() -> new ResourceNotFoundException("Direção participante não encontrada: " + directionId));

        if (!pd.getDemand().getId().equals(demandId)) {
            throw new BadRequestException("Direção participante não pertence à demanda informada");
        }

        String directionCode = pd.getDirectionCode();
        participatingDirections.delete(pd);
        invalidateComplexity(demand);
        demand.setUpdatedBy(actor(actorId));
        demands.save(demand);

        historyService.log(demand, "PARTICIPATING_DIRECTION_DELETED", null, null,
                "Direção participante removida: " + directionCode,
                actor(actorId), actor(actorId),
                Map.of("directionId", directionId.toString()));
    }

    private ParticipatingDirectionResponse mapParticipatingDirection(DemandParticipatingDirection pd) {
        return new ParticipatingDirectionResponse(
                pd.getId(),
            pd.getDirectionName(),
                pd.getDirectionCode(),
            pd.getAreaName(),
                pd.getAreaCode(),
                pd.getParticipationType(),
                pd.getObservations(),
                pd.getCreatedAt(),
                pd.getCreatedBy(),
                pd.getUpdatedAt(),
                pd.getUpdatedBy(),
                pd.getVersion()
        );
    }
}
