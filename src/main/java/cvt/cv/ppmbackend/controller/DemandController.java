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
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.service.DemandCapacityAnalysisService;
import cvt.cv.ppmbackend.service.DemandComplexityService;
import cvt.cv.ppmbackend.service.DemandScoringService;
import cvt.cv.ppmbackend.service.DemandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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

    public DemandController(DemandService demands, DemandScoringService scoring, DemandComplexityService complexity,
            DemandCapacityAnalysisService capacityAnalysis) {
        this.demands = demands;
        this.scoring = scoring;
        this.complexity = complexity;
        this.capacityAnalysis = capacityAnalysis;
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
            @RequestParam(required = false) UUID strategicPlanId,
            @RequestParam(required = false) UUID operationalPlanId,
            @RequestParam(required = false) UUID strategicPillarId,
            @RequestParam(required = false) UUID strategicObjectiveId,
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) String requester,
            @RequestParam(required = false) String businessArea,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo) {
        return demands.list(page, size, sort, search, status, type, origin, urgency,
                strategicPlanId, operationalPlanId, strategicPillarId, strategicObjectiveId, programId,
                requester, businessArea, createdFrom, createdTo);
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

    @GetMapping("/{id}")
    public DemandResponse get(@PathVariable UUID id) {
        return demands.get(id);
    }

    @GetMapping("/{id}/planning-history")
    public List<DemandPlanningCycleDtos.PlanningHistoryItem> planningHistory(@PathVariable UUID id) {
        return demands.planningHistory(id);
    }

    @GetMapping("/{id}/planning-cycles/{strategicPlanId}")
    public DemandPlanningCycleDtos.Response getCycle(@PathVariable UUID id, @PathVariable UUID strategicPlanId) {
        return demands.getCycle(id, strategicPlanId);
    }

    @PatchMapping("/{id}/planning-cycles/{strategicPlanId}")
    public DemandPlanningCycleDtos.Response updateCycleEnrichment(@PathVariable UUID id,
            @PathVariable UUID strategicPlanId, @Valid @RequestBody EnrichmentRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
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

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/approve")
    public DemandResponse approve(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.approveDecision(id, strategicPlanId, actor(user));
    }

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/conditionally-approve")
    public DemandResponse conditionallyApprove(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @Valid @RequestBody ConditionallyApproveRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.conditionallyApprove(id, strategicPlanId, req, actor(user));
    }

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/request-revision")
    public DemandResponse requestRevision(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @Valid @RequestBody RequestRevisionRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.requestRevision(id, strategicPlanId, req, actor(user));
    }

    @PostMapping("/{id}/planning-cycles/{strategicPlanId}/backlog")
    public DemandResponse moveToBacklog(@PathVariable UUID id, @PathVariable UUID strategicPlanId,
            @Valid @RequestBody BacklogRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.moveToBacklog(id, strategicPlanId, req, actor(user));
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
        return demands.update(id, req, actor(user));
    }

    @PatchMapping("/{id}")
    public DemandResponse patch(@PathVariable UUID id, @Valid @RequestBody Patch req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.patch(id, req, actor(user));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader(value = "X-User-Id", required = false) String user) {
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
        return demands.history(id);
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public DemandAttachmentResponse addAttachment(@PathVariable UUID id, @Valid @RequestBody AttachmentCreate req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.addAttachment(id, req, actor(user));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
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
        return demands.listParticipatingDirections(id);
    }

    @PostMapping("/{id}/participating-directions")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipatingDirectionResponse addParticipatingDirection(@PathVariable UUID id,
            @Valid @RequestBody ParticipatingDirectionCreate req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.addParticipatingDirection(id, req, actor(user));
    }

    @PutMapping("/{id}/participating-directions/{directionId}")
    public ParticipatingDirectionResponse updateParticipatingDirection(@PathVariable UUID id,
            @PathVariable UUID directionId,
            @Valid @RequestBody ParticipatingDirectionUpdate req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.updateParticipatingDirection(id, directionId, req, actor(user));
    }

    @DeleteMapping("/{id}/participating-directions/{directionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteParticipatingDirection(@PathVariable UUID id,
            @PathVariable UUID directionId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        demands.deleteParticipatingDirection(id, directionId, actor(user));
    }
}
