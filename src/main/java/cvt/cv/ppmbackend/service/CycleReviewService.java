package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cvt.cv.ppmbackend.dto.CycleDtos.Create;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.*;
import cvt.cv.ppmbackend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;

@Service
@Transactional
public class CycleReviewService {
    private final CycleReviewRepository reviews;
    private final CycleService cycles;
    private final ObjectMapper json;

    public CycleReviewService(CycleReviewRepository r, CycleService c, ObjectMapper j) {
        reviews = r;
        cycles = c;
        json = j;
    }

    public Map<String, Object> create(UUID sourceId, String user) {
        StrategicPlan s = cycles.entity(sourceId);
        int start = s.getStartYear() + 1, end = start + 2;
        ObjectNode d = json.createObjectNode();
        ObjectNode n = d.putObject("newCycle");
        n.put("name", "Ciclo Estrategico " + start + "-" + end);
        n.put("startYear", start);
        n.put("endYear", end);
        n.put("description", "");
        ObjectNode o = d.putObject("copyOptions");
        for (String x : List.of("pillars", "objectives", "programs", "kpis", "annualTargets"))
            o.put(x, true);
        d.putArray("objectiveActions");
        d.putArray("projectTransitions");
        d.putNull("destinationPlanId");
        CycleReview r = new CycleReview();
        r.setSourceCycle(s);
        r.setDraftJson(d.toString());
        r.setCreatedBy(user);
        r.setUpdatedBy(user);
        return view(reviews.save(r));
    }

    public Map<String, Object> get(UUID id) {
        return view(entity(id));
    }

    public Map<String, Object> patch(UUID id, JsonNode draft, String user) {
        CycleReview r = entity(id);
        if (r.getStatus() != CycleReviewStatus.DRAFT)
            conflict("REVIEW_NOT_EDITABLE", "Review is not editable");
        r.setDraftJson(draft.toString());
        r.setUpdatedBy(user);
        return view(reviews.save(r));
    }

    public Map<String, Object> validate(UUID id) {
        CycleReview r = entity(id);
        JsonNode d = read(r);
        List<Map<String, Object>> errors = new ArrayList<>(), warnings = new ArrayList<>();
        JsonNode n = d.path("newCycle");
        if (n.path("endYear").asInt() != n.path("startYear").asInt() + 2)
            errors.add(issue("INVALID_PERIOD", "New cycle must have three years"));
        int selected = 0;
        BigDecimal remaining = BigDecimal.ZERO;
        for (JsonNode t : d.path("projectTransitions")) {
            if (!t.path("selected").asBoolean())
                continue;
            selected++;
            UUID pid = uuid(t.path("projectId").asText(), errors);
            if (pid != null) {
                Project p = cycles.projects().findById(pid).orElse(null);
                if (p == null)
                    errors.add(issue("PROJECT_NOT_FOUND", "Project does not exist: " + pid));
                else {
                    if (p.getStatus() == ProjectStatus.ON_HOLD)
                        warnings.add(issue("PROJECT_BLOCKED", "Blocked project: " + pid));
                    if (p.getBudget() != null)
                        remaining = remaining.add(p.getBudget());
                }
            }
            String a = t.path("action").asText();
            if (Set.of("TRANSFER_REVISED", "REPRIORITIZE", "SUSPEND", "CANCEL").contains(a)
                    && t.path("justification").asText().isBlank())
                errors.add(issue("JUSTIFICATION_REQUIRED", "Justification is required for " + a));
        }
        BigDecimal planBudget = BigDecimal.ZERO;
        JsonNode dp = d.path("destinationPlanId");
        if (!dp.isMissingNode() && !dp.isNull() && !dp.asText().isBlank()) {
            try {
                OperationalPlan p = cycles.plans().findById(UUID.fromString(dp.asText())).orElse(null);
                if (p == null)
                    errors.add(issue("PLAN_NOT_FOUND", "Destination plan does not exist"));
                else {
                    planBudget = Optional.ofNullable(p.getApprovedBudget())
                            .orElse(Optional.ofNullable(p.getTotalBudget()).orElse(BigDecimal.ZERO));
                    if (p.getFiscalYear() < n.path("startYear").asInt()
                            || p.getFiscalYear() > n.path("endYear").asInt())
                        errors.add(issue("PLAN_INCOMPATIBLE", "Destination plan year is outside cycle"));
                }
            } catch (Exception e) {
                errors.add(issue("PLAN_INVALID", "Invalid destination plan id"));
            }
        }
        BigDecimal available = planBudget.subtract(remaining);
        int pct = planBudget.signum() > 0 ? remaining.multiply(BigDecimal.valueOf(100))
                .divide(planBudget, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
        if (pct > 60)
            warnings.add(issue("BUDGET_OVER_60", "Transition commits more than 60 percent"));
        return Map.of("errors", errors, "warnings", warnings, "counts", Map.of("projectsSelected", selected),
                "projectsSelected", selected, "remainingProjectBudget", remaining, "destinationPlanBudget", planBudget,
                "availableBudget", available, "occupancyPercentage", pct, "impacts", warnings);
    }

    public Map<String, Object> execute(UUID id, String key, String user) {
        if (key == null || key.isBlank())
            throw new DomainException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required",
                    Map.of());
        Optional<CycleReview> prior = reviews.findByIdempotencyKey(key);
        if (prior.isPresent())
            return result(prior.get());
        CycleReview r = reviews.locked(id).orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
        if (r.getStatus() == CycleReviewStatus.EXECUTED)
            return result(r);
        Map<String, Object> v = validate(id);
        if (!((List<?>) v.get("errors")).isEmpty())
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_INVALID", "Review has validation errors",
                    Map.of("validation", v));
        JsonNode n = read(r).path("newCycle");
        var c = cycles.create(new Create(n.path("name").asText(), n.path("startYear").asInt(),
                n.path("endYear").asInt(), n.path("description").asText()), user);
        r.setCreatedCycleId(c.id());
        r.setIdempotencyKey(key);
        r.setStatus(CycleReviewStatus.EXECUTED);
        r.setUpdatedBy(user);
        reviews.save(r);
        return Map.of("cycle", c, "summary", v, "review", view(r));
    }

    public Map<String, Object> submit(UUID id, String user) {
        CycleReview r = entity(id);
        if (r.getStatus() != CycleReviewStatus.DRAFT)
            conflict("REVIEW_INVALID_STATUS", "Only draft reviews can be submitted");
        if (!((List<?>) validate(id).get("errors")).isEmpty())
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_INVALID", "Review has validation errors",
                    Map.of());
        r.setStatus(CycleReviewStatus.SUBMITTED);
        r.setUpdatedBy(user);
        return view(r);
    }

    public void delete(UUID id) {
        CycleReview r = entity(id);
        if (r.getStatus() != CycleReviewStatus.DRAFT)
            conflict("REVIEW_DELETE_BLOCKED", "Only draft reviews can be deleted");
        reviews.delete(r);
    }

    private CycleReview entity(UUID id) {
        return reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private JsonNode read(CycleReview r) {
        try {
            return json.readTree(r.getDraftJson());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> view(CycleReview r) {
        return Map.ofEntries(Map.entry("id", r.getId()), Map.entry("sourceCycleId", r.getSourceCycle().getId()),
                Map.entry("status", r.getStatus()), Map.entry("draft", read(r)),
                Map.entry("version", Optional.ofNullable(r.getVersion()).orElse(0L)),
                Map.entry("createdAt", Optional.ofNullable(r.getCreatedAt()).map(Object::toString).orElse("")),
                Map.entry("updatedAt", Optional.ofNullable(r.getUpdatedAt()).map(Object::toString).orElse("")));
    }

    private Map<String, Object> result(CycleReview r) {
        return Map.of("createdCycleId", r.getCreatedCycleId(), "review", view(r), "idempotentReplay", true);
    }

    private Map<String, Object> issue(String c, String m) {
        return Map.of("code", c, "message", m);
    }

    private UUID uuid(String x, List<Map<String, Object>> e) {
        try {
            return UUID.fromString(x);
        } catch (Exception z) {
            e.add(issue("INVALID_UUID", "Invalid project id"));
            return null;
        }
    }

    private void conflict(String c, String m) {
        throw new DomainException(HttpStatus.CONFLICT, c, m, Map.of());
    }
}
