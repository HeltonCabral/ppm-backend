package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.ConditionalApprovalResponse;
import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.FinalApprovalResponse;
import cvt.cv.ppmbackend.dto.StrategicPlanCreateRequest;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.Overview;
import cvt.cv.ppmbackend.dto.StrategicPlanOverviewDtos.PlanListItem;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.service.StrategicPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/strategic-plans")
public class StrategicPlanController extends AbstractCrudController<StrategicPlan, StrategicPlanCreateRequest> {
    private final StrategicPlanService plans;

    public StrategicPlanController(StrategicPlanService s) {
        super(s);
        plans = s;
    }

    @Override
    @GetMapping
    public List<PlanListItem> findAll() {
        return plans.history();
    }

    @Override
    @PostMapping
    public ResponseEntity<StrategicPlan> create(@RequestBody(required = false) StrategicPlanCreateRequest request) {
        throw new BadRequestException(
                "Criação manual não é permitida. Utilize POST /strategic-plans/start-planning.");
    }

    @GetMapping("/overview")
    public Overview overview() {
        return plans.overview();
    }

    @GetMapping("/planning")
    public StrategicPlan planning() {
        return plans.getCurrentPlanningPlan();
    }

    @GetMapping("/active")
    public StrategicPlan active() {
        return plans.getActivePlan();
    }

    @GetMapping("/{id}/summary")
    public Map<String, Object> summary(@PathVariable UUID id) {
        return plans.summary(id);
    }

    @GetMapping("/{id}/dependencies")
    public Map<String, Long> dependencies(@PathVariable UUID id) {
        return plans.dependencyCounts(id);
    }

    @PostMapping("/start-planning")
    public ResponseEntity<StrategicPlan> startPlanning(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actorId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Sistema") String actorName) {
        StrategicPlan saved = plans.startPlanning(actorId, actorName);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().replacePath("/api/strategic-plans/{id}")
                .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(uri).body(saved);
    }

    @PostMapping("/{id}/conditional-approve")
    public ResponseEntity<ConditionalApprovalResponse> conditionalApprove(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actorId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Sistema") String actorName) {
        ConditionalApprovalResponse response = plans.conditionalApprove(id, actorId, actorName);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<FinalApprovalResponse> approve(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actorId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Sistema") String actorName) {
        FinalApprovalResponse response = plans.approve(id, actorId, actorName);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/activate")
    public StrategicPlan activate(@PathVariable UUID id) {
        return plans.activate(id);
    }

    @PostMapping("/{id}/close")
    public StrategicPlan close(@PathVariable UUID id) {
        return plans.close(id);
    }

    @PostMapping("/{id}/rollover")
    public ResponseEntity<StrategicPlan> rollover(@PathVariable UUID id) {
        StrategicPlan saved = plans.rollover(id);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().replacePath("/api/strategic-plans/{id}")
                .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(uri).body(saved);
    }
}
