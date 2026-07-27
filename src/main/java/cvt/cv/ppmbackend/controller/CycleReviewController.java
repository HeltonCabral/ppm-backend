package cvt.cv.ppmbackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import cvt.cv.ppmbackend.service.CycleReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/cycle-reviews")
@Tag(name = "Annual cycle reviews")
public class CycleReviewController {
    private final CycleReviewService s;

    public CycleReviewController(CycleReviewService s) {
        this.s = s;
    }

    private String user(String u) {
        return u == null || u.isBlank() ? "system" : u;
    }

    public record Start(UUID sourceCycleId) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Start r,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        return ResponseEntity.status(201).body(s.create(r.sourceCycleId(), user(u)));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return s.get(id);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> patch(@PathVariable UUID id, @RequestBody JsonNode d,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.patch(id, d, user(u));
    }

    @PostMapping("/{id}/validate")
    public Map<String, Object> validate(@PathVariable UUID id) {
        return s.validate(id);
    }

    @PostMapping("/{id}/execute")
    public Map<String, Object> execute(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.execute(id, key, user(u));
    }

    @PostMapping("/{id}/submit-approval")
    public Map<String, Object> submit(@PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String u) {
        return s.submit(id, user(u));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        s.delete(id);
    }
}
