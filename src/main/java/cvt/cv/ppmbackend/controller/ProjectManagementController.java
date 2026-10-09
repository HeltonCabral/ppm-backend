package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.*;
import cvt.cv.ppmbackend.enums.ProjectRiskStatus;
import cvt.cv.ppmbackend.enums.RiskLevel;
import cvt.cv.ppmbackend.service.ProjectChangeRequestService;
import cvt.cv.ppmbackend.service.ProjectClosureService;
import cvt.cv.ppmbackend.service.ProjectEventService;
import cvt.cv.ppmbackend.service.ProjectGuard;
import cvt.cv.ppmbackend.service.ProjectMilestoneService;
import cvt.cv.ppmbackend.service.ProjectPhaseService;
import cvt.cv.ppmbackend.service.ProjectRiskService;
import cvt.cv.ppmbackend.service.ProjectStatusReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Gestão de projetos: plano por fases, marcos, relatórios de estado, riscos, pedidos de alteração,
 * encerramento e histórico.
 */
@RestController
public class ProjectManagementController {
    private static final String USER = "X-User-Id";

    private final ProjectPhaseService phases;
    private final ProjectMilestoneService milestones;
    private final ProjectStatusReportService reports;
    private final ProjectRiskService risks;
    private final ProjectChangeRequestService changeRequests;
    private final ProjectClosureService closures;
    private final ProjectEventService events;
    private final ProjectGuard guard;

    public ProjectManagementController(ProjectPhaseService phases, ProjectMilestoneService milestones,
            ProjectStatusReportService reports, ProjectRiskService risks,
            ProjectChangeRequestService changeRequests, ProjectClosureService closures, ProjectEventService events,
            ProjectGuard guard) {
        this.phases = phases;
        this.milestones = milestones;
        this.reports = reports;
        this.risks = risks;
        this.changeRequests = changeRequests;
        this.closures = closures;
        this.events = events;
        this.guard = guard;
    }

    // ----- Fases -----

    @GetMapping("/projects/{id}/phases")
    public List<PhaseResponse> phases(@PathVariable UUID id) {
        return phases.list(id);
    }

    @PutMapping("/projects/{id}/phases/{phaseId}")
    public PhaseResponse updatePhase(@PathVariable UUID id, @PathVariable UUID phaseId,
            @RequestBody PhaseUpdateRequest request, @RequestHeader(value = USER, required = false) String user) {
        return phases.update(id, phaseId, request, user);
    }

    // ----- Marcos -----

    @GetMapping("/projects/{id}/milestones")
    public List<MilestoneResponse> milestones(@PathVariable UUID id) {
        return milestones.list(id);
    }

    @PostMapping("/projects/{id}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    public MilestoneResponse createMilestone(@PathVariable UUID id, @RequestBody MilestoneRequest request,
            @RequestHeader(value = USER, required = false) String user) {
        return milestones.create(id, request, user);
    }

    @PutMapping("/projects/{id}/milestones/{milestoneId}")
    public MilestoneResponse updateMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId,
            @RequestBody MilestoneRequest request, @RequestHeader(value = USER, required = false) String user) {
        return milestones.update(id, milestoneId, request, user);
    }

    @DeleteMapping("/projects/{id}/milestones/{milestoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId,
            @RequestHeader(value = USER, required = false) String user) {
        milestones.delete(id, milestoneId, user);
    }

    // ----- Relatórios de estado -----

    @GetMapping("/projects/{id}/status-reports")
    public List<StatusReportResponse> statusReports(@PathVariable UUID id) {
        return reports.list(id);
    }

    @PostMapping("/projects/{id}/status-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusReportResponse createStatusReport(@PathVariable UUID id, @RequestBody StatusReportRequest request,
            @RequestHeader(value = USER, required = false) String user) {
        return reports.create(id, request, user);
    }

    // ----- Riscos e problemas -----

    @GetMapping("/projects/{id}/risks")
    public List<RiskResponse> risks(@PathVariable UUID id) {
        return risks.list(id);
    }

    @PostMapping("/projects/{id}/risks")
    @ResponseStatus(HttpStatus.CREATED)
    public RiskResponse createRisk(@PathVariable UUID id, @RequestBody RiskRequest request,
            @RequestHeader(value = USER, required = false) String user) {
        return risks.create(id, request, user);
    }

    @PutMapping("/projects/{id}/risks/{riskId}")
    public RiskResponse updateRisk(@PathVariable UUID id, @PathVariable UUID riskId, @RequestBody RiskRequest request,
            @RequestHeader(value = USER, required = false) String user) {
        return risks.update(id, riskId, request, user);
    }

    @DeleteMapping("/projects/{id}/risks/{riskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRisk(@PathVariable UUID id, @PathVariable UUID riskId,
            @RequestHeader(value = USER, required = false) String user) {
        risks.delete(id, riskId, user);
    }

    /** Página /risks: {@code ?status=OPEN&level=HIGH,CRITICAL}, de todos os projetos. */
    @GetMapping("/project-risks")
    public List<RiskResponse> searchRisks(@RequestParam(required = false) List<ProjectRiskStatus> status,
            @RequestParam(required = false) List<RiskLevel> level) {
        return risks.search(status, level);
    }

    // ----- Pedidos de alteração -----

    @GetMapping("/projects/{id}/change-requests")
    public List<ChangeRequestResponse> changeRequests(@PathVariable UUID id) {
        return changeRequests.list(id);
    }

    @PostMapping("/projects/{id}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequestResponse createChangeRequest(@PathVariable UUID id, @RequestBody ChangeRequestCreate request,
            @RequestHeader(value = USER, required = false) String user) {
        return changeRequests.create(id, request, user);
    }

    @PostMapping("/projects/{id}/change-requests/{crId}/approve")
    public ChangeRequestResponse approve(@PathVariable UUID id, @PathVariable UUID crId,
            @RequestBody(required = false) DecisionRequest request,
            @RequestHeader(value = USER, required = false) String user) {
        return changeRequests.approve(id, crId, request, user);
    }

    @PostMapping("/projects/{id}/change-requests/{crId}/reject")
    public ChangeRequestResponse reject(@PathVariable UUID id, @PathVariable UUID crId,
            @RequestBody(required = false) DecisionRequest request,
            @RequestHeader(value = USER, required = false) String user) {
        return changeRequests.reject(id, crId, request, user);
    }

    @PostMapping("/projects/{id}/change-requests/{crId}/cancel")
    public ChangeRequestResponse cancel(@PathVariable UUID id, @PathVariable UUID crId,
            @RequestBody(required = false) DecisionRequest request,
            @RequestHeader(value = USER, required = false) String user) {
        return changeRequests.cancel(id, crId, request, user);
    }

    // ----- Encerramento -----

    @GetMapping("/projects/{id}/closure")
    public ClosureResponse closure(@PathVariable UUID id) {
        return closures.get(id);
    }

    /** Página /lessons: lições de todos os projetos. */
    @GetMapping("/lessons-learned")
    public List<LessonLearnedResponse> lessons() {
        return closures.allLessons();
    }

    // ----- Histórico -----

    @GetMapping("/projects/{id}/history")
    public List<EventResponse> history(@PathVariable UUID id, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        guard.require(id);
        return events.list(id, page, size);
    }
}
