package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.DemandDtos.*;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos.EnrichmentRequest;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos.TransitionRequest;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.DemandScoringResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.UpsertRequest;
import cvt.cv.ppmbackend.dto.CommitteeSuggestionResponse;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.DemandComplexityResponse;
import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.CapacityAnalysisResponse;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.CommitteeDashboardResponse;
import cvt.cv.ppmbackend.dto.DemandCommitteeActionDtos;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.service.DemandAccessService;
import cvt.cv.ppmbackend.service.DemandCapacityAnalysisService;
import cvt.cv.ppmbackend.service.DemandCommitteeDashboardService;
import cvt.cv.ppmbackend.service.DemandComplexityService;
import cvt.cv.ppmbackend.service.DemandScoringService;
import cvt.cv.ppmbackend.service.DemandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demands")
public class DemandController {
    private final DemandService demands;
    private final DemandScoringService scoring;
    private final DemandComplexityService complexity;
    private final DemandCapacityAnalysisService capacityAnalysis;
    private final DemandCommitteeDashboardService committeeDashboard;
    private final DemandAccessService access;

    public DemandController(DemandService demands, DemandScoringService scoring, DemandComplexityService complexity,
            DemandCapacityAnalysisService capacityAnalysis, DemandCommitteeDashboardService committeeDashboard,
            DemandAccessService access) {
        this.demands = demands;
        this.scoring = scoring;
        this.complexity = complexity;
        this.capacityAnalysis = capacityAnalysis;
        this.committeeDashboard = committeeDashboard;
        this.access = access;
    }

    private String actor(String header) {
        return header == null || header.isBlank() ? "system" : header;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DemandResponse create(@Valid @RequestBody Create req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.create(req, actor(user));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public DemandResponse duplicate(@PathVariable UUID id,
            @Valid @RequestBody(required = false) Duplicate req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireView(id);
        return demands.duplicate(id, req, actor(user));
    }

    @GetMapping
    public PagedDemandsResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<DemandPlanningStatus> status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) DemandNature nature,
            @RequestParam(required = false) UUID committeeId,
            @RequestParam(required = false) BigDecimal scoreMin,
            @RequestParam(required = false) BigDecimal scoreMax,
            @RequestParam(required = false) UUID strategicPlanId,
            @RequestParam(required = false) UUID operationalPlanId,
            @RequestParam(required = false) UUID strategicPillarId,
            @RequestParam(required = false) UUID strategicObjectiveId,
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) String requester,
            @RequestParam(required = false) String businessArea,
            @RequestParam(required = false) String directionCode,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.list(page, size, sort, search, status, type, origin, urgency, nature,
                committeeId, scoreMin, scoreMax,
                strategicPlanId, operationalPlanId, strategicPillarId, strategicObjectiveId, programId,
                requester, businessArea, directionCode, createdFrom, createdTo, actor(user));
    }

    @GetMapping("/strategic-plans/{strategicPlanId}/committee-dashboard")
    public CommitteeDashboardResponse getCommitteeDashboard(@PathVariable UUID strategicPlanId) {
        return committeeDashboard.dashboard(strategicPlanId);
    }

    @GetMapping("/portfolio-ranking")
    public PagedDemandsResponse listPortfolioRanked(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam UUID strategicPlanId,
            @RequestParam(required = false) String directionCode,
            @RequestParam(required = false) String areaCode) {
        return demands.listPortfolioRanked(page, size, strategicPlanId, directionCode, areaCode);
    }

    @GetMapping("/in-review")
    public PagedDemandsResponse listInReview(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID strategicPlanId) {
        return demands.listInReview(page, size, strategicPlanId);
    }

    @GetMapping("/{id}")
    public DemandResponse get(@PathVariable UUID id) {
        access.requireView(id);
        return demands.get(id);
    }

    @GetMapping("/{id}/planning-history")
    public List<DemandPlanningCycleDtos.PlanningHistoryItem> planningHistory(@PathVariable UUID id) {
        access.requireView(id);
        return demands.planningHistory(id);
    }

    @GetMapping("/{id}/planning-cycles/{strategicPlanId}")
    public DemandPlanningCycleDtos.Response getCycle(@PathVariable UUID id, @PathVariable UUID strategicPlanId) {
        access.requireView(id);
        return demands.getCycle(id, strategicPlanId);
    }

    @PatchMapping("/{id}/planning-cycles/{strategicPlanId}")
    public DemandPlanningCycleDtos.Response updateCycleEnrichment(@PathVariable UUID id,
            @PathVariable UUID strategicPlanId, @Valid @RequestBody EnrichmentRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        return demands.updateCycleEnrichment(id, strategicPlanId, req, actor(user));
    }

    @GetMapping("/{id}/planning-cycles/{strategicPlanId}/committee-suggestion")
    public CommitteeSuggestionResponse committeeSuggestion(@PathVariable UUID id, @PathVariable UUID strategicPlanId) {
        return demands.committeeSuggestion(id, strategicPlanId);
    }

    @GetMapping("/in-priorization/by-username")
    public List<DemandResponse> listInPriorizationByUsername(@RequestParam String username,
            @RequestParam UUID strategicPlanId) {
        return demands.listInPriorizationByUsername(username, strategicPlanId);
    }

    @PatchMapping("/{id}/planning-cycles/{strategicPlanId}/assign-committee")
    public DemandResponse assignCommittee(
            @PathVariable UUID id,
            @PathVariable UUID strategicPlanId,
            @Valid @RequestBody AssignCommitteeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.assignCommittee(id, strategicPlanId, request, actor(user));
    }

    @GetMapping("/{id}/planning-cycles/{strategicPlanId}/committee-actions")
    public List<DemandCommitteeActionDtos.Response> committeeActions(@PathVariable UUID id,
            @PathVariable UUID strategicPlanId) {
        return demands.committeeActions(id, strategicPlanId);
    }

    /**
     * Pedido de revisão — disponível a Comités especializados e ao Comité Estratégico.
     * A aprovação já não é individual: resulta da seleção de um cenário de portfólio.
     */
    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/request-revision")
    public DemandResponse requestRevision(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @Valid @RequestBody DemandCommitteeActionDtos.RequestRevisionRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.requestRevision(id, strategicPlanId, req, actor(user));
    }

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/resolve-revision")
    public DemandResponse resolveRevision(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @RequestBody(required = false) DemandCommitteeActionDtos.ResolveRevisionRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.resolveRevision(id, strategicPlanId, req, actor(user));
    }
    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/send-to-strategic-committee")
    public DemandResponse sendToStrategicCommittee(
            @PathVariable UUID id,
            @PathVariable UUID strategicPlanId,
            @RequestBody(required = false) SendToStrategicCommitteeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        String reason = request != null ? request.reason() : null;
        return demands.sendToStrategicCommittee(id, strategicPlanId, reason, actor(user));
    }

    @PostMapping("/send-to-strategic-committee/bulk")
    public List<DemandResponse> sendToStrategicCommitteeBulk(
            @RequestParam UUID strategicPlanId,
            @Valid @RequestBody SendToStrategicCommitteeBulkRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.sendToStrategicCommitteeBulk(request.demandIds(), strategicPlanId, request.reason(), actor(user));
    }

    @PutMapping("/{id}")
    public DemandResponse update(@PathVariable UUID id, @Valid @RequestBody Update req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        return demands.update(id, req, actor(user));
    }

    @PatchMapping("/{id}")
    public DemandResponse patch(@PathVariable UUID id, @Valid @RequestBody Patch req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        return demands.patch(id, req, actor(user));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        demands.delete(id, actor(user));
    }

    @PatchMapping("/{id}/planning-cycles/{strategicPlanId}/status")
    public DemandResponse changeStatus(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @Valid @RequestBody TransitionRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.changeStatus(id, strategicPlanId, req, actor(user));
    }

    @GetMapping("/{id}/history")
    public List<DemandHistoryResponse> history(@PathVariable UUID id) {
        access.requireView(id);
        return demands.history(id);
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public DemandAttachmentResponse addAttachment(@PathVariable UUID id, @Valid @RequestBody AttachmentCreate req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        return demands.addAttachment(id, req, actor(user));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        demands.deleteAttachment(id, attachmentId, actor(user));
    }

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/convert-to-project")
    public ConvertResponse convertToProject(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @Valid @RequestBody ConvertToProject req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.convertToProject(id, strategicPlanId, req, actor(user));
    }

    @GetMapping("/{id}/planning-cycles/{strategicPlanId}/scoring")
    public DemandScoringResponse getScoring(@PathVariable UUID id, @PathVariable UUID strategicPlanId) {
        access.requireView(id);
        return scoring.getByDemandAndPlan(id, strategicPlanId);
    }

    @PutMapping("/{id}/planning-cycles/{strategicPlanId}/scoring")
    public DemandScoringResponse upsertScoring(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @Valid @RequestBody UpsertRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return scoring.upsert(id, strategicPlanId, req, actor(user));
    }

    @GetMapping("/{id}/planning-cycles/{strategicPlanId}/capacity-analysis")
    public CapacityAnalysisResponse getCapacityAnalysis(@PathVariable UUID id, @PathVariable UUID strategicPlanId) {
        access.requireView(id);
        return capacityAnalysis.analyze(id, strategicPlanId);
    }

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/calculate-complexity")
    public DemandComplexityResponse calculateComplexity(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return complexity.calculate(id, strategicPlanId, actor(user));
    }

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/reprioritize-portfolio-rank")
    public ReprioritizePortfolioRankResponse reprioritizePortfolioRank(@PathVariable UUID id,
            @PathVariable UUID strategicPlanId,
            @Valid @RequestBody ReprioritizePortfolioRankRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.reprioritizePortfolioRank(id, strategicPlanId, req, actor(user));
    }

    @GetMapping("/{id}/participating-directions")
    public List<ParticipatingDirectionResponse> listParticipatingDirections(@PathVariable UUID id) {
        access.requireView(id);
        return demands.listParticipatingDirections(id);
    }

    @PostMapping("/{id}/participating-directions")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipatingDirectionResponse addParticipatingDirection(@PathVariable UUID id,
            @Valid @RequestBody ParticipatingDirectionCreate req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        return demands.addParticipatingDirection(id, req, actor(user));
    }

    @PutMapping("/{id}/participating-directions/{directionId}")
    public ParticipatingDirectionResponse updateParticipatingDirection(@PathVariable UUID id,
            @PathVariable UUID directionId,
            @Valid @RequestBody ParticipatingDirectionUpdate req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        return demands.updateParticipatingDirection(id, directionId, req, actor(user));
    }

    @DeleteMapping("/{id}/participating-directions/{directionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteParticipatingDirection(@PathVariable UUID id,
            @PathVariable UUID directionId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        access.requireEdit(id);
        demands.deleteParticipatingDirection(id, directionId, actor(user));
    }
}
