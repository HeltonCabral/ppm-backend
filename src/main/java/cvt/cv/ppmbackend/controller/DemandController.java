package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.DemandDtos.*;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.DemandScoringResponse;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.UpsertRequest;
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

    public DemandController(DemandService demands, DemandScoringService scoring) {
        this.demands = demands;
        this.scoring = scoring;
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
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String initialPriority,
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
        return demands.list(page, size, sort, search, status, type, origin, initialPriority, urgency,
                strategicPlanId, operationalPlanId, strategicPillarId, strategicObjectiveId, programId,
                requester, businessArea, createdFrom, createdTo);
    }

    @GetMapping("/{id}")
    public DemandResponse get(@PathVariable UUID id) {
        return demands.get(id);
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

    @PatchMapping("/{id}/status")
    public DemandResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusPatch req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.changeStatus(id, req, actor(user));
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

    @PostMapping("/{id}/convert-to-project")
    public ConvertResponse convertToProject(@PathVariable UUID id, @Valid @RequestBody ConvertToProject req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return demands.convertToProject(id, req, actor(user));
    }

    @GetMapping("/{id}/scoring")
    public DemandScoringResponse getScoring(@PathVariable UUID id) {
        return scoring.getByDemand(id);
    }

    @PutMapping("/{id}/scoring")
    public DemandScoringResponse upsertScoring(@PathVariable UUID id, @Valid @RequestBody UpsertRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return scoring.upsert(id, req, actor(user));
    }
}
