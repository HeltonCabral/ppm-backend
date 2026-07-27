package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.*;
import cvt.cv.ppmbackend.entity.*;
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
    private static final Set<String> PRIORITY_CODES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> EFFORT_CODES = Set.of("XS", "S", "M", "L", "XL");
    private static final Set<String> STATUS_CODES = Set.of("RECEIVED", "UNDER_ANALYSIS", "UNDER_PRIORITIZATION",
            "READY_FOR_COMMITTEE", "APPROVED", "REJECTED", "CONVERTED_TO_PROJECT", "ARCHIVED");
    private static final Set<String> CAPACITY_CODES = Set.of("NOT_ANALYZED", "AVAILABLE", "LIMITED", "UNAVAILABLE");
    private static final Set<String> RISK_CODES = Set.of("NOT_EVALUATED", "LOW", "MEDIUM", "HIGH");
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
    private final ProjectRepository projects;
    private final LookupValueService lookups;

    public DemandService(DemandRepository demands, DemandAttachmentRepository attachments, DemandHistoryRepository history,
            DemandCodeService codeService, DemandHistoryService historyService, StrategicPlanService strategicPlans,
            OperationalPlanService operationalPlans, StrategicPillarService pillars, StrategicObjectiveService objectives,
            ProgramService programs, ProjectRepository projects, LookupValueService lookups) {
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
        this.projects = projects;
        this.lookups = lookups;
    }

    public DemandResponse create(Create req, String actorId) {
        Demand demand = new Demand();
        demand.setCode(codeService.nextCode());
        demand.setStatus("RECEIVED");
        demand.setCreatedBy(actor(actorId));
        demand.setUpdatedBy(actor(actorId));
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
        apply(toCreate(req), demand, false);
        demand.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(demand);
        historyService.log(saved, "UPDATED", old, saved.getStatus(), "Demanda atualizada", actor(actorId),
                actor(actorId), Map.of());
        return map(saved);
    }

    @Transactional
    public DemandResponse patch(UUID id, Patch req, String actorId) {
        Demand d = entity(id);
        Create merged = mergePatch(d, req);
        String old = d.getStatus();
        apply(merged, d, false);
        d.setUpdatedBy(actor(actorId));
        Demand saved = demands.save(d);
        historyService.log(saved, "UPDATED", old, saved.getStatus(), "Demanda atualizada parcialmente", actor(actorId),
                actor(actorId), Map.of());
        return map(saved);
    }

    @Transactional
    public void delete(UUID id, String actorId) {
        Demand d = entity(id);
        d.setDeletedAt(Instant.now());
        d.setUpdatedBy(actor(actorId));
        historyService.log(d, "ARCHIVED", d.getStatus(), "ARCHIVED", "Demanda arquivada por eliminação lógica",
                actor(actorId), actor(actorId), Map.of());
    }

    @Transactional
    public DemandResponse changeStatus(UUID id, StatusPatch req, String actorId) {
        Demand d = entity(id);
        String from = d.getStatus();
        String to = norm(req.status());
        validateStatusValue(to);
        validateTransition(from, to, req.reason());
        d.setStatus(to);
        d.setUpdatedBy(actor(actorId));
        if ("REJECTED".equals(to))
            d.setRejectionReason(req.reason());
        Demand saved = demands.save(d);
        String event = eventTypeForTransition(to);
        historyService.log(saved, event, from, to, req.reason(), actor(actorId), actor(actorId), Map.of());
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
        if (!"APPROVED".equals(d.getStatus())) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_NOT_APPROVED", "Somente demandas aprovadas podem ser convertidas",
                    Map.of("status", d.getStatus()));
        }
        if (d.getConvertedProject() != null) {
            throw domain(HttpStatus.CONFLICT, "DEMAND_ALREADY_CONVERTED", "Demanda já convertida",
                    Map.of("projectId", d.getConvertedProject().getId()));
        }

        Program program = req.programId() != null ? programs.findById(req.programId())
                : Optional.ofNullable(d.getProgram())
                        .orElseThrow(() -> new BadRequestException("programId é obrigatório para conversão"));
        validateDates(req.startDate(), req.endDate());

        Project p = new Project();
        p.setName(req.projectName());
        p.setDescription(d.getDescription());
        p.setProgram(program);
        p.setOperationalPlan(d.getOperationalPlan());
        p.setDomain(null);
        p.setBusinessArea(Optional.ofNullable(d.getArea()).orElse("N/A"));
        p.setProjectType(null);
        p.setResponsibleDirection(d.getDirection());
        p.setResponsibleTeam(null);
        p.setProjectManager(req.projectManager() == null || req.projectManager().isBlank() ? actor(actorId)
                : req.projectManager());
        p.setStatus(ProjectStatus.DRAFT);
        p.setProjectPhase(null);
        p.setMainSupplier(null);
        p.setImpactedSystem(d.getImpactedSystem());
        p.setScheduleStatus(ExecutiveStatus.GREEN);
        p.setCostStatus(ExecutiveStatus.GREEN);
        p.setRiskStatus(ExecutiveStatus.GREEN);
        p.setValueStatus(ExecutiveStatus.GREEN);
        p.setExpectedBenefits(d.getExpectedBenefit());
        p.setPlannedStartDate(req.startDate());
        p.setStartDate(req.startDate());
        p.setPlannedEndDate(req.endDate());
        p.setEndDate(req.endDate());
        p.setPriority(mapPriority(d.getInitialPriority()));
        p.setRanking(d.getPortfolioRank());
        p.setBudgetLine(d.getDomain() != null ? d.getDomain().getCode() : null);
        p.setBudget(d.getEstimatedBudget());
        p.setPlanType(null);
        p.setDelayReasons(null);
        p.setSourceDemandId(d.getId());

        Project savedProject = projects.save(p);
        d.setConvertedProject(savedProject);
        d.setStatus("CONVERTED_TO_PROJECT");
        d.setUpdatedBy(actor(actorId));
        Demand savedDemand = demands.save(d);

        historyService.log(savedDemand, "CONVERTED_TO_PROJECT", "APPROVED", "CONVERTED_TO_PROJECT",
                "Demanda convertida em projeto", actor(actorId), actor(actorId),
                Map.of("projectId", savedProject.getId().toString()));

        return new ConvertResponse(
                new DemandConvertInfo(savedDemand.getId(), savedDemand.getCode(), savedDemand.getStatus(),
                        savedProject.getId()),
                new ProjectConvertInfo(savedProject.getId(), null, savedProject.getName()));
    }

    @Transactional
    public PagedDemandsResponse list(int page, int size, String sort, String search, String status, String type,
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
        if (status != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), norm(status)));
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
        d.setScoreValue(req.scoreValue());
        d.setScoreEffort(req.scoreEffort());
        d.setScoreRisk(req.scoreRisk());
        d.setScoreTotal(req.scoreTotal());
        d.setPortfolioRank(req.portfolioRank());
        d.setApprovalType(req.approvalType() == null ? null : norm(req.approvalType()));
        d.setCommitteeDecision(req.committeeDecision() == null ? null : norm(req.committeeDecision()));
        d.setRejectionReason(req.rejectionReason());

        if (creating)
            d.setStatus("RECEIVED");

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

    private void validateTransition(String from, String to, String reason) {
        if (Objects.equals(from, to))
            return;
        Map<String, Set<String>> next = new HashMap<>();
        next.put("RECEIVED", Set.of("UNDER_ANALYSIS","UNDER_PRIORITIZATION", "ARCHIVED"));
        next.put("UNDER_ANALYSIS", Set.of("UNDER_PRIORITIZATION", "REJECTED", "ARCHIVED", "RECEIVED"));
        next.put("UNDER_PRIORITIZATION", Set.of("READY_FOR_COMMITTEE", "REJECTED", "ARCHIVED", "UNDER_ANALYSIS"));
        next.put("READY_FOR_COMMITTEE",
                Set.of("APPROVED", "REJECTED", "UNDER_ANALYSIS", "UNDER_PRIORITIZATION", "ARCHIVED"));
        next.put("APPROVED", Set.of("CONVERTED_TO_PROJECT", "ARCHIVED", "UNDER_ANALYSIS"));
        next.put("REJECTED", Set.of("UNDER_ANALYSIS", "ARCHIVED"));
        next.put("CONVERTED_TO_PROJECT", Set.of("ARCHIVED"));
        next.put("ARCHIVED", Set.of());

        boolean ok = next.getOrDefault(from, Set.of()).contains(to);
        if (!ok) {
            throw domain(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Transição de status inválida",
                    Map.of("from", from, "to", to));
        }

        if (("REJECTED".equals(to) || "ARCHIVED".equals(to)) && (reason == null || reason.isBlank())) {
            throw new BadRequestException("reason é obrigatório para status " + to);
        }
    }

    private String eventTypeForTransition(String to) {
        return switch (to) {
            case "READY_FOR_COMMITTEE" -> "SUBMITTED_TO_COMMITTEE";
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
        ProjectSummary convertedProject = mapProject(d.getConvertedProject());
        return new DemandResponse(d.getId(), d.getCode(), d.getTitle(), d.getDescription(), d.getRequester(), d.getArea(),
                d.getDirection(), d.getSponsor(), d.getTypeId(), typeCode, d.getOrigin(), d.getEasyVistaRef(),
                d.getStrategicPlanId(),
                d.getOperationalPlanId(), d.getStrategicPillarId(), d.getStrategicObjectiveId(), d.getProgramId(),
                d.getDomainId(), domainCode, d.getImpactedSystem(), d.getInitialPriority(), d.getEstimatedEffort(),
                d.getExpectedImpact(),
                d.getExpectedBenefit(), d.getUrgency(), d.getEstimatedBudget(), d.getDesiredDate(), d.getNotes(),
                d.getStatus(), d.getCapacityStatus(), d.getRiskStatus(), d.getRisksIdentified(),
                d.getDependenciesIdentified(), d.getScoreValue(), d.getScoreEffort(), d.getScoreRisk(), d.getScoreTotal(),
                d.getPortfolioRank(), d.getApprovalType(), d.getCommitteeDecision(), d.getRejectionReason(),
                d.getConvertedProjectId(), d.getCreatedAt(), d.getCreatedBy(), d.getUpdatedAt(), d.getUpdatedBy(),
                d.getVersion(), typeData, domainData, strategicPlan, operationalPlan, strategicPillar,
                strategicObjective, program, convertedProject, att);
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

    private ProjectSummary mapProject(Project project) {
        if (project == null)
            return null;
        return new ProjectSummary(project.getId(), project.getName(),
                project.getStatus() != null ? project.getStatus().name() : null);
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
                p.scoreValue() != null ? p.scoreValue() : d.getScoreValue(),
                p.scoreEffort() != null ? p.scoreEffort() : d.getScoreEffort(),
                p.scoreRisk() != null ? p.scoreRisk() : d.getScoreRisk(),
                p.scoreTotal() != null ? p.scoreTotal() : d.getScoreTotal(),
                p.portfolioRank() != null ? p.portfolioRank() : d.getPortfolioRank(),
                p.approvalType() != null ? p.approvalType() : d.getApprovalType(),
                p.committeeDecision() != null ? p.committeeDecision() : d.getCommitteeDecision(),
                p.rejectionReason() != null ? p.rejectionReason() : d.getRejectionReason(),
                null);
    }

    private Create toCreate(Update u) {
        return new Create(u.title(), u.description(), u.requester(), u.area(), u.direction(), u.sponsor(), u.typeId(),
                u.origin(), u.easyVistaRef(), u.strategicPlanId(), u.operationalPlanId(), u.strategicPillarId(),
            u.strategicObjectiveId(), u.programId(), u.domainId(), u.impactedSystem(), u.initialPriority(),
                u.estimatedEffort(), u.expectedImpact(), u.expectedBenefit(), u.urgency(), u.estimatedBudget(),
                u.desiredDate(), u.notes(), u.capacityStatus(), u.riskStatus(), u.risksIdentified(),
                u.dependenciesIdentified(), u.scoreValue(), u.scoreEffort(), u.scoreRisk(), u.scoreTotal(),
                u.portfolioRank(), u.approvalType(), u.committeeDecision(), u.rejectionReason(), null);
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
}
