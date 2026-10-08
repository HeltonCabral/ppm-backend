package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.*;
import cvt.cv.ppmbackend.dto.DemandCommitteeActionDtos;

import cvt.cv.ppmbackend.dto.DemandDependencyDtos;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos;
import cvt.cv.ppmbackend.dto.CommitteeSuggestionResponse;
import cvt.cv.ppmbackend.entity.*;

import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.enums.DemandNature;
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
    // Estados que encerram o ciclo de revisão: já voltou a priorizado ou entrou no plano.
    private static final List<DemandPlanningStatus> REVIEW_SETTLED_STATUSES = List.of(PRIORITIZED, IN_PLAN);

    private final DemandRepository demands;
    private final DemandPlanningCycleRepository planningCycles;
    private final DemandAttachmentRepository attachments;
    private final DemandHistoryRepository history;
    private final DemandParticipatingDirectionRepository participatingDirections;
    private final DemandAreaRepository demandAreas;
    private final DemandParticipatingDirectionAreaRepository participatingDirectionAreas;
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
    private final DemandProfileRequirementService profileRequirements;
    private final DemandSuccessCriterionService successCriteria;
    private final SupplierRepository supplierRepository;
    private final DemandPlanningCycleService planningCycleService;
    private final DemandDependencyService dependencyService;
    private final cvt.cv.ppmbackend.repository.PhaseModelRepository phaseModels;
    private final cvt.cv.ppmbackend.repository.DemandCommitteeActionRepository committeeActions;
    private final DemandCustomPlanningService customPlanning;
    private final DemandAccessService access;

    public DemandService(DemandRepository demands, DemandPlanningCycleRepository planningCycles,
            DemandAttachmentRepository attachments, DemandHistoryRepository history,
            DemandParticipatingDirectionRepository participatingDirections,
            DemandAreaRepository demandAreas,
            DemandParticipatingDirectionAreaRepository participatingDirectionAreas,
            DemandCodeService codeService, DemandHistoryService historyService,
            CommitteeService committees, CommitteeSuggestionService committeeSuggestions,
            ProjectService projects, ProgramService programs, OperationalPlanService operationalPlans,
            LookupValueService lookups,
            DemandScoringService scoringService, DemandScoreLifecycleService scoreLifecycle,
            DemandProfileRequirementService profileRequirements,
            DemandSuccessCriterionService successCriteria,
            SupplierRepository supplierRepository, DemandPlanningCycleService planningCycleService,
            DemandDependencyService dependencyService,
            cvt.cv.ppmbackend.repository.PhaseModelRepository phaseModels,
            cvt.cv.ppmbackend.repository.DemandCommitteeActionRepository committeeActions,
            DemandCustomPlanningService customPlanning,
            DemandAccessService access) {
        this.demands = demands;
        this.planningCycles = planningCycles;
        this.attachments = attachments;
        this.history = history;
        this.participatingDirections = participatingDirections;
        this.demandAreas = demandAreas;
        this.participatingDirectionAreas = participatingDirectionAreas;
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
        this.profileRequirements = profileRequirements;
        this.successCriteria = successCriteria;
        this.supplierRepository = supplierRepository;
        this.planningCycleService = planningCycleService;
        this.dependencyService = dependencyService;
        this.phaseModels = phaseModels;
        this.committeeActions = committeeActions;
        this.customPlanning = customPlanning;
        this.access = access;
    }

    public DemandResponse create(Create req, String actorId) {
        Demand demand = new Demand();
        demand.setCode(codeService.nextCode());
        demand.setCreatedBy(actor(actorId));
        demand.setUpdatedBy(actor(actorId));
        apply(req, demand);
        Demand saved = demands.save(demand);
        DemandPlanningCycle cycle = planningCycleService.createInitialCycle(saved, req.strategicPillarId(),
                req.strategicElementId(), req.strategicObjectiveId(), actor(actorId));
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
                DemandParticipatingDirection savedDirection = participatingDirections.save(direction);
                syncParticipatingDirectionAreas(savedDirection, pd.areas(), actorId);
            });
        }
        syncDemandAreas(saved, req.areas(), actorId);
        profileRequirements.sync(saved, req.profileRequirements());
        successCriteria.sync(saved, req.successCriteria());
        customPlanning.apply(saved, req.customPlanning(), actor(actorId));
        historyService.log(saved, "CREATED", null, cycle.getStatus().name(), "Demanda criada", actor(actorId),
                actor(actorId), Map.of("code", saved.getCode()));
        return map(saved);
    }

    /**
     * Cria uma nova Demanda a partir dos dados de uma existente. A cópia recebe
     * código próprio e entra num novo ciclo de planeamento em DRAFT; scoring,
     * complexidade, rankings, decisões de comité e histórico da origem não são
     * copiados.
     */
    @Transactional
    public DemandResponse duplicate(UUID id, Duplicate req, String actorId) {
        Demand source = entity(id);
        Duplicate options = req != null ? req : new Duplicate(null, null, null, null, null, null);

        DemandResponse createdResponse = create(toDuplicateCreate(source, options), actorId);
        Demand copy = entity(createdResponse.id());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceDemandId", source.getId().toString());
        metadata.put("sourceDemandCode", source.getCode());
        historyService.log(copy, "DUPLICATED", null, DRAFT.name(),
                "Demanda criada por cópia da demanda " + source.getCode(), actor(actorId), actor(actorId), metadata);
        return map(copy);
    }

    private Create toDuplicateCreate(Demand source, Duplicate options) {
        List<AttachmentInput> attachmentInputs = null;
        if (copyEnabled(options.copyAttachments())) {
            attachmentInputs = attachments.findByDemandIdOrderByCreatedAtDesc(source.getId()).stream()
                    .sorted(Comparator.comparing(DemandAttachment::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(a -> new AttachmentInput(a.getName(), a.getUrl(), a.getContentType()))
                    .toList();
        }

        List<ParticipatingDirectionCreate> directionInputs = null;
        if (copyEnabled(options.copyParticipatingDirections())) {
            directionInputs = participatingDirections.findByDemandIdOrderByCreatedAtAsc(source.getId()).stream()
                    .map(pd -> new ParticipatingDirectionCreate(pd.getDirectionName(), pd.getDirectionCode(),
                            pd.getAreaName(), pd.getAreaCode(), pd.getParticipationType(), pd.getObservations(),
                            copyParticipatingDirectionAreas(pd.getId())))
                    .toList();
        }

        // Perfis inativos não podem ser associados a novas Demandas, por isso ficam de fora da cópia.
        List<DemandProfileRequirementInput> profileInputs = null;
        if (copyEnabled(options.copyProfileRequirements())) {
            profileInputs = profileRequirements.list(source.getId()).stream()
                    .filter(DemandProfileRequirementResponse::profileActive)
                    .map(r -> new DemandProfileRequirementInput(null, r.profileId(), r.requiredQuantity(),
                            r.phases().stream().map(DemandPhaseRef::phaseId).toList()))
                    .toList();
        }

        List<DemandSuccessCriterionInput> criterionInputs = null;
        if (copyEnabled(options.copySuccessCriteria())) {
            criterionInputs = successCriteria.list(source.getId()).stream()
                    .map(c -> new DemandSuccessCriterionInput(c.description()))
                    .toList();
        }

        UUID strategicPillarId = null;
        UUID strategicElementId = null;
        UUID strategicObjectiveId = null;
        if (copyEnabled(options.copyStrategicFraming())) {
            Optional<DemandPlanningCycle> sourceCycle = planningCycleService.getCurrentPlanningCycle(source.getId());
            strategicPillarId = sourceCycle.map(DemandPlanningCycle::getStrategicPillar)
                    .map(StrategicPillar::getId).orElse(null);
            strategicElementId = sourceCycle.map(DemandPlanningCycle::getStrategicElement)
                    .map(StrategicElement::getId).orElse(null);
            strategicObjectiveId = sourceCycle.map(DemandPlanningCycle::getStrategicObjective)
                    .map(StrategicObjective::getId).orElse(null);
        }

        Set<UUID> supplierIds = new HashSet<>();
        source.getSuppliers().forEach(supplier -> supplierIds.add(supplier.getId()));

        return new Create(
                duplicateTitle(options.title(), source.getTitle()),
                strategicPillarId,
                strategicElementId,
                strategicObjectiveId,
                source.getSummary(),
                source.getRequester(),
                source.getAreaName(),
                source.getAreaCode(),
                source.getDirectionName(),
                source.getDirectionCode(),
                source.getDirectionParticipationType(),
                source.getNature(),
                source.getSponsor(),
                source.getTypeId(),
                source.getOrigin(),
                source.getEasyVistaRef(),
                supplierIds,
                source.getDomainId(),
                source.getBudgetLineId(),
                source.getImpactedSystem(),
                source.getCurrentEvidence(),
                source.getProblemStatement(),
                source.getDesiredOutcome(),
                source.getProposedResponse(),
                source.getUrgency(),
                source.getEstimatedBudget(),
                source.getDesiredDate(),
                source.getMandatoryDeadline(),
                source.getNotes(),
                source.getRisksIdentified(),
                source.getDependenciesIdentified(),
                null,
                attachmentInputs,
                directionInputs,
                profileInputs,
                criterionInputs,
                copyDemandAreas(source.getId()),
                null);
    }

    private boolean copyEnabled(Boolean flag) {
        return flag == null || flag;
    }

    private String duplicateTitle(String requestedTitle, String sourceTitle) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle.trim();
        }
        String candidate = "Cópia de " + sourceTitle;
        return candidate.length() <= 250 ? candidate : candidate.substring(0, 250);
    }

    @Transactional
    public DemandResponse update(UUID id, Update req, String actorId) {
        Demand demand = entity(id);
        ScoreRelevantState previousScoreInputs = scoreRelevantState(demand);
        ComplexityRelevantState previousComplexityInputs = complexityRelevantState(demand);
        LocalDate previousDesiredDate = demand.getDesiredDate();
        UUID previousTypeId = demand.getType() == null ? null : demand.getType().getId();
        apply(toCreate(req), demand);
        handleScoreRelevantChanges(demand, previousScoreInputs);
        invalidateComplexityIfNeeded(demand, previousComplexityInputs,
                req.participatingDirections() != null || req.profileRequirements() != null);
        refreshPlannedStartDateIfNeeded(demand, previousDesiredDate);
        demand.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(demand);

        if (req.participatingDirections() != null) {
            participatingDirectionAreas.deleteByParticipatingDirection_Demand_Id(id);
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
                DemandParticipatingDirection savedDirection = participatingDirections.save(direction);
                syncParticipatingDirectionAreas(savedDirection, pd.areas(), actorId);
            });
        }
        pruneProfileRequirementsOnTypeChange(saved, previousTypeId);
        profileRequirements.sync(saved, req.profileRequirements());
        successCriteria.sync(saved, req.successCriteria());
        syncDemandAreas(saved, req.areas(), actorId);
        applyStrategicFramingIfRequested(saved, req.strategicPillarId(), req.strategicElementId(),
                req.strategicObjectiveId(), actorId);

        applyCustomPlanningChange(saved, previousTypeId, req.customPlanning(), actorId);
        historyService.log(saved, "UPDATED", null, null, "Demanda atualizada", actor(actorId), actor(actorId), Map.of());
        return map(saved);
    }

    @Transactional
    public DemandResponse patch(UUID id, Patch req, String actorId) {
        Demand d = entity(id);
        ScoreRelevantState previousScoreInputs = scoreRelevantState(d);
        ComplexityRelevantState previousComplexityInputs = complexityRelevantState(d);
        LocalDate previousDesiredDate = d.getDesiredDate();
        UUID previousTypeId = d.getType() == null ? null : d.getType().getId();
        Create merged = mergePatch(d, req);
        apply(merged, d);
        handleScoreRelevantChanges(d, previousScoreInputs);
        invalidateComplexityIfNeeded(d, previousComplexityInputs,
                req.participatingDirections() != null || req.profileRequirements() != null);
        refreshPlannedStartDateIfNeeded(d, previousDesiredDate);
        d.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(d);

        if (req.participatingDirections() != null) {
            participatingDirectionAreas.deleteByParticipatingDirection_Demand_Id(id);
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
                DemandParticipatingDirection savedDirection = participatingDirections.save(direction);
                syncParticipatingDirectionAreas(savedDirection, pd.areas(), actorId);
            });
        }
        pruneProfileRequirementsOnTypeChange(saved, previousTypeId);
        profileRequirements.sync(saved, req.profileRequirements());
        successCriteria.sync(saved, req.successCriteria());
        syncDemandAreas(saved, req.areas(), actorId);
        applyStrategicFramingIfRequested(saved, req.strategicPillarId(), req.strategicElementId(),
                req.strategicObjectiveId(), actorId);

        applyCustomPlanningChange(saved, previousTypeId, req.customPlanning(), actorId);
        historyService.log(saved, "UPDATED", null, null, "Demanda atualizada parcialmente", actor(actorId),
                actor(actorId), Map.of());
        return map(saved);
    }

    private void applyStrategicFramingIfRequested(Demand demand, UUID strategicPillarId, UUID strategicElementId,
            UUID strategicObjectiveId, String actorId) {
        if (strategicPillarId == null && strategicElementId == null && strategicObjectiveId == null) {
            return;
        }
        planningCycleService.getCurrentPlanningCycle(demand.getId()).ifPresent(cycle ->
                planningCycleService.updateEnrichment(cycle, strategicPillarId, strategicElementId,
                        strategicObjectiveId, null, null, null, actor(actorId)));
    }

    @Transactional
    public void delete(UUID id, String actorId) {
        Demand d = entity(id);
        List<DemandPlanningCycle> openCycles = planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(id).stream()
                .filter(c -> c.getStatus() != BACKLOG)
                .toList();
        for (DemandPlanningCycle cycle : openCycles) {
            planningCycleService.transitionStatus(cycle, BACKLOG, "Demanda arquivada por eliminação lógica", actorId);
        }
        d.setDeletedAt(Instant.now());
        d.setUpdatedBy(actor(actorId));
        historyService.log(d, "ARCHIVED", null, BACKLOG.name(), "Demanda arquivada por eliminação lógica",
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
                cb.equal(root.get("status"), IN_PRIORITIZATION));
               /* root.get("scoreStatus").in(
                        DemandScoreLifecycleService.SCORE_NOT_CALCULATED,
                        DemandScoreLifecycleService.SCORE_OUTDATED))*/ 
        return planningCycles.findAll(spec, Sort.by(Sort.Direction.ASC, "cycleEntryAt")).stream()
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

    /**
     * Pedido de revisão — a única decisão individual que subsiste no modelo de cenários.
     * A aprovação resulta da seleção de um cenário de portfólio.
     */
    @Transactional
    public DemandResponse requestRevision(UUID id, UUID strategicPlanId,
            DemandCommitteeActionDtos.RequestRevisionRequest req, String actorId) {
        Demand demand = entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        planningCycleService.requestRevision(cycle, req.justification(), actor(actorId), actor(actorId));
        return map(demand);
    }

    @Transactional
    public DemandResponse resolveRevision(UUID id, UUID strategicPlanId,
            DemandCommitteeActionDtos.ResolveRevisionRequest req, String actorId) {
        Demand demand = entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        planningCycleService.resolveRevision(cycle, req == null ? null : req.resolutionNotes(),
                req != null && Boolean.TRUE.equals(req.requiresRepriorization()), actor(actorId), actor(actorId));
        return map(demand);
    }

    @Transactional
    public List<DemandCommitteeActionDtos.Response> committeeActions(UUID id, UUID strategicPlanId) {
        entity(id);
        DemandPlanningCycle cycle = planningCycleService.resolveForMutation(id, strategicPlanId);
        return planningCycleService.actionHistory(cycle.getId());
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
        p.setDescription(req.description() != null ? req.description() : d.getSummary());
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
        p.setExpectedBenefits(req.expectedBenefits() != null ? req.expectedBenefits() : d.getDesiredOutcome());
        p.setPriority(req.priority() != null ? req.priority() : Priority.MEDIUM);
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

    /**
     * Demandas "em revisão": pediram revisão (ação REQUEST_REVISION) e o ciclo ainda não voltou a um
     * estado de revisão concluída (PRIORITIZED/IN_PLAN). {@code strategicPlanId} nulo = todos os planos.
     */
    @Transactional
    public PagedDemandsResponse listInReview(int page, int size, UUID strategicPlanId) {
        Set<UUID> demandIds = committeeActions.findDemandIdsPendingReview(REVIEW_SETTLED_STATUSES, strategicPlanId);
        if (demandIds.isEmpty()) {
            return new PagedDemandsResponse(List.of(), page, size, 0, 0);
        }
        Specification<Demand> spec = (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                root.get("id").in(demandIds));
        Page<Demand> p = demands.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PagedDemandsResponse(p.getContent().stream().map(this::map).toList(), page, size,
                p.getTotalElements(), p.getTotalPages());
    }

    @Transactional
    public PagedDemandsResponse list(int page, int size, String sort, String search, List<DemandPlanningStatus> status,
            String type, String origin, String urgency, DemandNature nature,
            UUID committeeId, BigDecimal scoreMin, BigDecimal scoreMax, UUID strategicPlanId,
            UUID operationalPlanId, UUID strategicPillarId, UUID strategicObjectiveId, UUID programId,
            String requester, String businessArea, String directionCode, LocalDate createdFrom, LocalDate createdTo,
            String createdBy) {
        if (scoreMin != null && scoreMax != null && scoreMin.compareTo(scoreMax) > 0) {
            throw new BadRequestException("scoreMin não pode ser maior que scoreMax");
        }
        String sortField = sort == null || sort.isBlank() ? "createdAt" : sort;
        Specification<Demand> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (access.filterEnabled()) {
            // A regra "Ver" já inclui as demandas criadas pelo utilizador.
            spec = spec.and(access.visibleToCurrentUser());
        } else if (createdBy != null && !createdBy.isBlank()) {
            final String creator = createdBy.trim();
            spec = spec.and((r, q, cb) -> cb.equal(r.get("createdBy"), creator));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("summary")), like),
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
        if (urgency != null)
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("urgency")), norm(urgency)));
        if (nature != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("nature"), nature));
        if (requester != null)
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("requester")), "%" + requester.toLowerCase() + "%"));
        if (businessArea != null)
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("areaName")), "%" + businessArea.toLowerCase() + "%"));
        if (directionCode != null && !directionCode.isBlank())
            spec = spec.and((r, q, cb) -> cb.equal(cb.upper(r.get("directionCode")), norm(directionCode)));
        if (createdFrom != null) {
            Instant from = createdFrom.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        }
        if (createdTo != null) {
            Instant to = createdTo.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("createdAt"), to));
        }

        boolean cycleFiltered = strategicPlanId != null || operationalPlanId != null || strategicPillarId != null
                || strategicObjectiveId != null || programId != null || committeeId != null
                || scoreMin != null || scoreMax != null || (status != null && !status.isEmpty());
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
            if (committeeId != null)
                cycleSpec = cycleSpec
                        .and((r, q, cb) -> cb.equal(r.get("responsibleCommittee").get("id"), committeeId));
            if (scoreMin != null)
                cycleSpec = cycleSpec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("scoreTotal"), scoreMin));
            if (scoreMax != null)
                cycleSpec = cycleSpec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("scoreTotal"), scoreMax));
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
                req.strategicElementId(), req.strategicObjectiveId(), req.programId(), req.capacityStatus(),
                req.riskStatus(), actor(actorId));
        return mapCycle(saved);
    }

    private DemandPlanningCycleDtos.Response mapCycle(DemandPlanningCycle c) {
        return new DemandPlanningCycleDtos.Response(
                c.getId(), c.getDemand().getId(),
                mapStrategicPlan(c.getStrategicPlan()), mapOperationalPlan(c.getOperationalPlan()),
                mapStrategicPillar(c.getStrategicPillar()), mapStrategicElement(c.getStrategicElement()),
                mapStrategicObjective(c.getStrategicObjective()),
                mapProgram(c.getProgram()), c.getStatus(), mapCommittee(c.getResponsibleCommittee()),
                c.isInStrategicCommittee(), c.getStrategicCommitteeAt(),
                c.getScoreTotal(), c.getScoreStatus(), c.getScoreCalculatedAt(), c.getScoreInvalidatedAt(),
                c.getScoreInvalidationReason(), scoreLifecycle.previousScoreSnapshot(c), c.getPortfolioRank(),
                c.getDirectionRank(), c.getCommitteeRank(), c.getRankBeforeBacklog(), c.getCapacityStatus(), c.getRiskStatus(),
                planningCycleService.actionHistory(c.getId()),
                c.getReprioritizationReason(), c.getReprioritizationJustification(),
                c.getReprioritizedAt(), c.getReprioritizedBy(), c.isCarriedOver(),
                c.getPreviousCycle() != null ? c.getPreviousCycle().getId() : null, c.getCycleEntryAt(),
                c.getConvertedProject() != null ? c.getConvertedProject().getId() : null,
                c.getConvertedProgram() != null ? c.getConvertedProgram().getId() : null, c.getConvertedAt(),
                c.getConvertedBy(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion(),
                scoreLifecycle.listHistory(c.getId()));
    }

    private StrategicPillarSummary mapStrategicPillar(StrategicPillar pillar) {
        if (pillar == null)
            return null;
        return new StrategicPillarSummary(pillar.getId(), pillar.getName(), pillar.getDescription());
    }

    private StrategicElementSummary mapStrategicElement(StrategicElement element) {
        if (element == null)
            return null;
        return new StrategicElementSummary(element.getId(), element.getName(), element.getDescription());
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

    private boolean underReview(DemandPlanningCycle cycle) {
        return cycle.getStatus() == IN_REVIEW;
    }

    @Transactional
    public List<DemandPlanningCycleDtos.PlanningHistoryItem> planningHistory(UUID id) {
        entity(id);
        return planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(id).stream()
                .map(c -> new DemandPlanningCycleDtos.PlanningHistoryItem(
                        c.getStrategicPlan().getStartYear(), c.getStrategicPlan().getName(),
                        c.getOperationalPlan().getName(), c.getStatus(), c.getScoreTotal(), c.getPortfolioRank(),
                        underReview(c), c.isCarriedOver()))
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
        d.setSummary(req.summary());
        d.setRequester(req.requester());
        d.setAreaName(req.areaName());
        d.setAreaCode(req.areaCode());
        d.setDirectionName(req.directionName());
        d.setDirectionCode(req.directionCode());
        d.setDirectionParticipationType(req.directionParticipationType());
        d.setNature(req.nature() != null ? req.nature() : DemandNature.NORMAL);
        d.setSponsor(req.sponsor());
        d.setType(req.typeId() == null ? null : requireActivePhaseModel(req.typeId()));
        d.setOrigin(normalizedOrigin);
        d.setEasyVistaRef(req.easyVistaRef());
        syncSuppliers(d, req.supplierIds());
        d.setDomain(req.domainId() == null ? null : lookups.findById(req.domainId()));
        d.setBudgetLine(req.budgetLineId() == null ? null : lookups.findById(req.budgetLineId()));
        d.setImpactedSystem(req.impactedSystem());
        d.setCurrentEvidence(req.currentEvidence());
        d.setProblemStatement(req.problemStatement());
        d.setDesiredOutcome(req.desiredOutcome());
        d.setProposedResponse(req.proposedResponse());
        d.setUrgency(req.urgency() == null ? null : norm(req.urgency()));
        d.setEstimatedBudget(req.estimatedBudget());
        d.setDesiredDate(req.desiredDate());
        d.setMandatoryDeadline(req.mandatoryDeadline());
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

      
        validateLookupOrEnum("DEMAND_ORIGIN", d.getOrigin(), Set.of("MANUAL", "EASYVISTA"));
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
        String budgetLineCode = d.getBudgetLine() != null ? d.getBudgetLine().getCode() : null;
        PhaseModelSummary typeData = mapPhaseModel(d.getType());
        LookupValueSummary domainData = mapLookupValue(d.getDomain());
        LookupValueSummary budgetLineData = mapLookupValue(d.getBudgetLine());
        Optional<DemandPlanningCycle> currentPlanningCycle = planningCycleService.getCurrentPlanningCycle(d.getId());
        DemandPlanningCycleDtos.Response currentCycle = currentPlanningCycle.map(this::mapCycle).orElse(null);
        StrategicPillarSummary strategicPillar = currentPlanningCycle.map(DemandPlanningCycle::getStrategicPillar)
                .map(this::mapStrategicPillar).orElse(null);
        StrategicElementSummary strategicElement = currentPlanningCycle.map(DemandPlanningCycle::getStrategicElement)
                .map(this::mapStrategicElement).orElse(null);
        StrategicObjectiveSummary strategicObjective = currentPlanningCycle.map(DemandPlanningCycle::getStrategicObjective)
                .map(this::mapStrategicObjective).orElse(null);
        List<SupplierSummary> supplierSummaries = d.getSuppliers().stream()
                .sorted(Comparator.comparing(Supplier::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::mapSupplier)
                .toList();
        List<DemandSuccessCriterionResponse> successCriteriaResponses = successCriteria.list(d.getId());
        List<DemandDependencyDtos.Response> dependencyResponses = dependencyService.listByDemand(d.getId());
        return new DemandResponse(d.getId(), d.getCode(), d.getTitle(), strategicPillar, strategicElement,
                strategicObjective, d.getSummary(), d.getRequester(), d.getAreaName(),
            d.getAreaCode(), d.getDirectionName(), d.getDirectionCode(), d.getDirectionParticipationType(), d.getNature(), d.getSponsor(), d.getTypeId(), typeCode, d.getOrigin(), d.getEasyVistaRef(),
                d.getDomainId(), domainCode, d.getBudgetLineId(), budgetLineCode, d.getImpactedSystem(),
                d.getCurrentEvidence(), d.getProblemStatement(),
                d.getDesiredOutcome(), d.getProposedResponse(), d.getUrgency(), d.getEstimatedBudget(), d.getDesiredDate(), d.getMandatoryDeadline(), d.getNotes(),
                d.getRisksIdentified(),
                d.getDependenciesIdentified(), d.getDirectionsCount(), d.getProfilesCount(), d.getTotalResources(),
                d.getDependenciesCount(), d.getComplexityScore(), d.getComplexity(), d.getEstimatedDurationMonths(),
                d.getPlannedStartDate(),
                d.getCreatedAt(), d.getCreatedBy(), d.getUpdatedAt(), d.getUpdatedBy(),
                d.getVersion(), typeData, domainData, budgetLineData, currentCycle,
                att, participatingDirs, profileRequirementResponses,
                supplierSummaries, successCriteriaResponses, dependencyResponses,
                listDemandAreas(d.getId()), customPlanning.buildResponse(d),
                access.permissionsFor(d,
                        participatingDirs.stream().map(ParticipatingDirectionResponse::directionCode).toList(),
                        currentPlanningCycle));
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

    private PhaseModelSummary mapPhaseModel(PhaseModel model) {
        if (model == null)
            return null;
        return new PhaseModelSummary(model.getId(), model.getCode(), model.getName(), model.isActive());
    }

    private PhaseModel requireActivePhaseModel(UUID id) {
        PhaseModel model = phaseModels.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo de fases não encontrado: " + id));
        if (!model.isActive()) {
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "PHASE_MODEL_INACTIVE",
                    "O modelo de fases selecionado deve estar ativo.",
                    Map.of("typeId", id.toString()));
        }
        return model;
    }

    private void pruneProfileRequirementsOnTypeChange(Demand demand, UUID previousTypeId) {
        UUID currentTypeId = demand.getType() == null ? null : demand.getType().getId();
        if (!Objects.equals(previousTypeId, currentTypeId)) {
            profileRequirements.handleTypeChange(demand);
        }
    }

    /**
     * Aplica a alteração de planeamento personalizado num update/patch: alterar o Tipo apaga a
     * personalização; depois aplica o campo {@code customPlanning} do pedido (omitido/null/objeto).
     */
    private void applyCustomPlanningChange(Demand demand, UUID previousTypeId,
            com.fasterxml.jackson.databind.JsonNode node, String actorId) {
        UUID currentTypeId = demand.getType() == null ? null : demand.getType().getId();
        if (!Objects.equals(previousTypeId, currentTypeId)) {
            customPlanning.clear(demand);
        }
        customPlanning.apply(demand, node, actor(actorId));
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
                .filter(c -> c.getStatus() != IN_PLAN && c.getStatus() != BACKLOG
                        && c.getConvertedProject() == null && c.getConvertedProgram() == null)
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
                demand.getSummary(),
                demand.getTypeId(),
                demand.getDomainId(),
                demand.getImpactedSystem(),
                demand.getCurrentEvidence(),
                demand.getProblemStatement(),
                demand.getDesiredOutcome(),
                demand.getProposedResponse(),
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
            String summary,
            UUID typeId,
            UUID domainId,
            String impactedSystem,
            String currentEvidence,
            String problemStatement,
            String desiredOutcome,
            String proposedResponse,
            String urgency,
            BigDecimal estimatedBudget,
            LocalDate desiredDate,
            String risksIdentified,
            String dependenciesIdentified) {
    }

    private Create mergePatch(Demand d, Patch p) {
        return new Create(
                p.title() != null ? p.title() : d.getTitle(),
                null,
                null,
                null,
                p.summary() != null ? p.summary() : d.getSummary(),
                p.requester() != null ? p.requester() : d.getRequester(),
                p.areaName() != null ? p.areaName() : d.getAreaName(),
                p.areaCode() != null ? p.areaCode() : d.getAreaCode(),
                p.directionName() != null ? p.directionName() : d.getDirectionName(),
                p.directionCode() != null ? p.directionCode() : d.getDirectionCode(),
                p.directionParticipationType() != null ? p.directionParticipationType() : d.getDirectionParticipationType(),
                p.nature() != null ? p.nature() : d.getNature(),
                p.sponsor() != null ? p.sponsor() : d.getSponsor(),
                p.typeId() != null ? p.typeId() : d.getTypeId(),
                p.origin() != null ? p.origin() : d.getOrigin(),
                p.easyVistaRef() != null ? p.easyVistaRef() : d.getEasyVistaRef(),
                p.supplierIds(),
                p.domainId() != null ? p.domainId() : d.getDomainId(),
                p.budgetLineId() != null ? p.budgetLineId() : d.getBudgetLineId(),
                p.impactedSystem() != null ? p.impactedSystem() : d.getImpactedSystem(),
                p.currentEvidence() != null ? p.currentEvidence() : d.getCurrentEvidence(),
                p.problemStatement() != null ? p.problemStatement() : d.getProblemStatement(),
                p.desiredOutcome() != null ? p.desiredOutcome() : d.getDesiredOutcome(),
                p.proposedResponse() != null ? p.proposedResponse() : d.getProposedResponse(),
                p.urgency() != null ? p.urgency() : d.getUrgency(),
                p.estimatedBudget() != null ? p.estimatedBudget() : d.getEstimatedBudget(),
                p.desiredDate() != null ? p.desiredDate() : d.getDesiredDate(),
                p.mandatoryDeadline() != null ? p.mandatoryDeadline() : d.getMandatoryDeadline(),
                p.notes() != null ? p.notes() : d.getNotes(),
                p.risksIdentified() != null ? p.risksIdentified() : d.getRisksIdentified(),
                p.dependenciesIdentified() != null ? p.dependenciesIdentified() : d.getDependenciesIdentified(),
                p.dependenciesCount() != null ? p.dependenciesCount() : d.getDependenciesCount(),
                null,
                p.participatingDirections(),
                p.profileRequirements(),
                p.successCriteria(),
                p.areas(),
                null);
    }

    private Create toCreate(Update u) {
        return new Create(u.title(), null, null, null, u.summary(), u.requester(), u.areaName(), u.areaCode(), u.directionName(), u.directionCode(), u.directionParticipationType(), u.nature(), u.sponsor(), u.typeId(),
                u.origin(), u.easyVistaRef(), u.supplierIds(), u.domainId(), u.budgetLineId(), u.impactedSystem(), u.currentEvidence(),
                u.problemStatement(), u.desiredOutcome(), u.proposedResponse(), u.urgency(), u.estimatedBudget(),
                u.desiredDate(), u.mandatoryDeadline(), u.notes(), u.risksIdentified(),
                u.dependenciesIdentified(), u.dependenciesCount(),
                null, u.participatingDirections(), u.profileRequirements(), u.successCriteria(), u.areas(), null);
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

        // Conjunto repriorizável = ranking do portefólio do plano (mesmo de
        // /demands/portfolio-ranking e /planning-scenarios/plan/{id}/demands): todos os ciclos com
        // portfolioRank atribuído, independentemente do status (PRIORITIZED, IN_PLAN, ...).
        Specification<DemandPlanningCycle> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("strategicPlan").get("id"), strategicPlanId),
                cb.isNotNull(root.get("portfolioRank")));
        List<DemandPlanningCycle> rankedCycles = new ArrayList<>(planningCycles.findAll(spec));

        rankedCycles.sort(Comparator.comparingInt(c -> c.getPortfolioRank() != null ? c.getPortfolioRank() : Integer.MAX_VALUE));

        if (rankedCycles.stream().noneMatch(c -> c.getId().equals(cycle.getId()))) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_NOT_IN_PORTFOLIO_RANKING",
                    "A Demanda não faz parte do ranking do portefólio deste plano e não pode ser repriorizada",
                    Map.of("status", cycle.getStatus().name(),
                            "portfolioRank", cycle.getPortfolioRank() == null ? "null"
                                    : cycle.getPortfolioRank().toString()));
        }

        int total = rankedCycles.size();
        int newPosition = req.newPosition();
        if (newPosition < 1 || newPosition > total) {
            throw new BadRequestException("A posição deve estar entre 1 e " + total + ".");
        }

        Integer currentRank = cycle.getPortfolioRank();
        if (currentRank != null && currentRank == newPosition) {
            throw new BadRequestException(
                    "A nova posição deve ser diferente da posição actual (#" + currentRank + ").");
        }

        Map<UUID, Integer> previousRanks = new HashMap<>();
        for (DemandPlanningCycle c : rankedCycles) {
            previousRanks.put(c.getId(), c.getPortfolioRank());
        }

        rankedCycles.removeIf(c -> c.getId().equals(cycle.getId()));
        rankedCycles.add(newPosition - 1, cycle);

        for (int i = 0; i < rankedCycles.size(); i++) {
            rankedCycles.get(i).setPortfolioRank(i + 1);
        }

        cycle.setReprioritizationReason(req.reprioritizationReason());
        cycle.setReprioritizationJustification(req.reprioritizationJustification());
        cycle.setReprioritizedAt(Instant.now());
        cycle.setReprioritizedBy(actor(actorId));

        planningCycles.saveAll(rankedCycles);

        historyService.log(demand, "PORTFOLIO_RANK_CHANGED", cycle.getStatus().name(), cycle.getStatus().name(),
                "Ranking repriorizado: " + previousRanks.get(cycle.getId()) + " → " + newPosition,
                actor(actorId), actor(actorId),
                Map.of("previousRank", String.valueOf(previousRanks.get(cycle.getId())), "newRank", String.valueOf(newPosition),
                       "reason", req.reprioritizationReason()));

        List<RankedDemandInfo> affectedDemands = rankedCycles.stream()
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
        syncParticipatingDirectionAreas(saved, req.areas(), actorId);
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
        syncParticipatingDirectionAreas(saved, req.areas(), actorId);
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
        participatingDirectionAreas.deleteByParticipatingDirectionId(directionId);
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
                pd.getVersion(),
                listParticipatingDirectionAreas(pd.getId())
        );
    }

    private List<DirectionAreaResponse> listDemandAreas(UUID demandId) {
        return demandAreas.findByDemandIdOrderByCreatedAtAsc(demandId).stream()
                .map(a -> new DirectionAreaResponse(a.getId(), a.getAreaCode(), a.getAreaName(), a.getRole()))
                .toList();
    }

    private List<DirectionAreaResponse> listParticipatingDirectionAreas(UUID directionId) {
        return participatingDirectionAreas.findByParticipatingDirectionIdOrderByCreatedAtAsc(directionId).stream()
                .map(a -> new DirectionAreaResponse(a.getId(), a.getAreaCode(), a.getAreaName(), a.getRole()))
                .toList();
    }

    private List<DirectionAreaInput> copyDemandAreas(UUID demandId) {
        List<DemandArea> current = demandAreas.findByDemandIdOrderByCreatedAtAsc(demandId);
        if (current.isEmpty()) {
            return null;
        }
        return current.stream()
                .map(a -> new DirectionAreaInput(a.getAreaCode(), a.getAreaName(), a.getRole()))
                .toList();
    }

    private List<DirectionAreaInput> copyParticipatingDirectionAreas(UUID directionId) {
        List<DemandParticipatingDirectionArea> current = participatingDirectionAreas
                .findByParticipatingDirectionIdOrderByCreatedAtAsc(directionId);
        if (current.isEmpty()) {
            return null;
        }
        return current.stream()
                .map(a -> new DirectionAreaInput(a.getAreaCode(), a.getAreaName(), a.getRole()))
                .toList();
    }

    private void syncDemandAreas(Demand demand, List<DirectionAreaInput> inputs, String actorId) {
        if (inputs == null) {
            return;
        }
        validateAreas(inputs, "areas");
        if (!inputs.isEmpty()) {
            validateResponsibleMatchesRoot(demand.getAreaCode(), demand.getAreaName(), inputs, "areas");
        }
        demandAreas.deleteByDemandId(demand.getId());
        String actor = actor(actorId);
        for (DirectionAreaInput input : inputs) {
            DemandArea area = new DemandArea();
            area.setDemand(demand);
            area.setAreaCode(input.areaCode());
            area.setAreaName(input.areaName());
            area.setRole(input.role());
            area.setCreatedBy(actor);
            area.setUpdatedBy(actor);
            demandAreas.save(area);
        }
    }

    private void syncParticipatingDirectionAreas(DemandParticipatingDirection direction,
            List<DirectionAreaInput> inputs, String actorId) {
        if (inputs == null) {
            return;
        }
        String field = "participatingDirections[" + direction.getDirectionCode() + "].areas";
        validateAreas(inputs, field);
        if (!inputs.isEmpty()) {
            validateResponsibleMatchesRoot(direction.getAreaCode(), direction.getAreaName(), inputs, field);
        }
        participatingDirectionAreas.deleteByParticipatingDirectionId(direction.getId());
        String actor = actor(actorId);
        for (DirectionAreaInput input : inputs) {
            DemandParticipatingDirectionArea area = new DemandParticipatingDirectionArea();
            area.setParticipatingDirection(direction);
            area.setAreaCode(input.areaCode());
            area.setAreaName(input.areaName());
            area.setRole(input.role());
            area.setCreatedBy(actor);
            area.setUpdatedBy(actor);
            participatingDirectionAreas.save(area);
        }
    }

    private void validateAreas(List<DirectionAreaInput> inputs, String field) {
        int responsibleCount = 0;
        int intervenientCount = 0;
        Set<String> seenCodes = new HashSet<>();
        for (DirectionAreaInput input : inputs) {
            if (input.areaCode() == null || input.areaCode().isBlank()) {
                throw new BadRequestException(field + ": areaCode é obrigatório");
            }
            if (input.role() == null) {
                throw new BadRequestException(field + ": role é obrigatório");
            }
            String key = input.areaCode().trim().toUpperCase(Locale.ROOT);
            if (!seenCodes.add(key)) {
                throw new BadRequestException(field + ": a mesma área não pode repetir-se na direção (" + input.areaCode() + ")");
            }
            if (input.role() == AreaRole.RESPONSIBLE) {
                responsibleCount++;
            } else {
                intervenientCount++;
            }
        }
        if (responsibleCount > 1) {
            throw new BadRequestException(field + ": só é permitida uma área RESPONSIBLE por direção");
        }
        if (intervenientCount > 0 && responsibleCount == 0) {
            throw new BadRequestException(field + ": áreas INTERVENIENT exigem uma área RESPONSIBLE");
        }
    }

    private void validateResponsibleMatchesRoot(String rootAreaCode, String rootAreaName,
            List<DirectionAreaInput> inputs, String field) {
        DirectionAreaInput responsible = inputs.stream()
                .filter(a -> a.role() == AreaRole.RESPONSIBLE)
                .findFirst()
                .orElse(null);
        if (responsible == null) {
            return;
        }
        if (rootAreaCode == null || rootAreaCode.isBlank()) {
            throw new BadRequestException(field + ": areaCode da direção é obrigatório quando 'areas' é enviado");
        }
        if (!responsible.areaCode().trim().equalsIgnoreCase(rootAreaCode.trim())) {
            throw new BadRequestException(field + ": areaCode/areaName da direção devem coincidir com a área RESPONSIBLE");
        }
        if (responsible.areaName() != null && !responsible.areaName().isBlank()
                && rootAreaName != null && !rootAreaName.isBlank()
                && !responsible.areaName().trim().equalsIgnoreCase(rootAreaName.trim())) {
            throw new BadRequestException(field + ": areaCode/areaName da direção devem coincidir com a área RESPONSIBLE");
        }
    }
}
