package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.CycleDtos.*;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import cvt.cv.ppmbackend.service.CycleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/cycles")
@Tag(name = "Strategic cycles")
public class CycleController {
    private final CycleService s;

    public CycleController(CycleService s) {
        this.s = s;
    }

    private String user(String h) {
        return h == null || h.isBlank() ? "system" : h;
    }

    @GetMapping
    public PageResponse<Response> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String search,
            @RequestParam(required = false) StrategicPlanStatus status,
            @RequestParam(required = false) Integer startYear, @RequestParam(required = false) Integer endYear,
            @RequestParam(defaultValue = "startYear") String sort, @RequestParam(defaultValue = "desc") String order) {
        return s.list(page, pageSize, search, status, startYear, endYear, sort, order);
    }

    @GetMapping("/active")
    public Response active() {
        return s.active();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return s.get(id);
    }

    @GetMapping("/{id}/summary")
    public Map<String, Object> summary(@PathVariable UUID id) {
        return s.summary(id);
    }

    @GetMapping("/{id}/dependencies")
    public Map<String, Long> deps(@PathVariable UUID id) {
        s.entity(id);
        return s.dependencyCounts(id);
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody Create r,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        Response x = s.create(r, user(u));
        return ResponseEntity.created(URI.create("/api/v1/cycles/" + x.id())).body(x);
    }

    @PatchMapping("/{id}")
    public Response patch(@PathVariable UUID id, @Valid @RequestBody Patch r,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.patch(id, r, user(u));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader(value = "X-User-Id", required = false) String u) {
        s.delete(id, user(u));
    }

    @PostMapping("/{id}/submit-review")
    public Response submit(@PathVariable UUID id, @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.transition(id, "submit-review", null, user(u));
    }

    @PostMapping("/{id}/approve")
    public Response approve(@PathVariable UUID id, @Valid @RequestBody Decision d,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.transition(id, "approve", d.comment(), user(u));
    }

    @PostMapping("/{id}/activate")
    public Response activate(@PathVariable UUID id, @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.transition(id, "activate", null, user(u));
    }

    @PostMapping("/{id}/reject")
    public Response reject(@PathVariable UUID id, @Valid @RequestBody Decision d,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.transition(id, "reject", d.comment(), user(u));
    }

    @PostMapping("/{id}/close")
    public Response close(@PathVariable UUID id, @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.transition(id, "close", null, user(u));
    }

    @GetMapping("/{id}/plans")
    public List<OperationalPlan> plans(@PathVariable UUID id) {
        s.entity(id);
        return s.plans().findByStrategicPlan_Id(id);
    }

    @GetMapping("/{id}/objectives")
    public List<StrategicObjective> objectives(@PathVariable UUID id) {
        s.entity(id);
        return s.objectives().findByStrategicPlan_Id(id);
    }

    @GetMapping("/{id}/programs")
    public List<Program> programs(@PathVariable UUID id) {
        s.entity(id);
        return s.programs().findByStrategicObjectives_StrategicPlan_Id(id);
    }

    @GetMapping("/{id}/projects")
    public List<Project> projects(@PathVariable UUID id) {
        s.entity(id);
        return s.plans().findByStrategicPlan_Id(id).stream()
                .flatMap(x -> s.projects().findByOperationalPlan_Id(x.getId()).stream()).toList();
    }

    @GetMapping("/{id}/demands")
    public List<Object> demands(@PathVariable UUID id) {
        s.entity(id);
        return List.of();
    }

    @GetMapping("/{id}/audit-log")
    public Page<CycleAuditLog> audit(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        s.entity(id);
        return s.audit().findByCycle_Id(id, PageRequest.of(page, pageSize, Sort.by("createdAt").descending()));
    }
}
