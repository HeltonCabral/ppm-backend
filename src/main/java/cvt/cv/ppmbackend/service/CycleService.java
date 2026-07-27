package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CycleDtos.*;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.*;
import cvt.cv.ppmbackend.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class CycleService {
    private final StrategicPlanRepository cycles;
    private final OperationalPlanRepository plans;
    private final StrategicObjectiveRepository objectives;
    private final ProgramRepository programs;
    private final ProjectRepository projects;
    private final CycleAuditLogRepository audit;

    public CycleService(StrategicPlanRepository c, OperationalPlanRepository p, StrategicObjectiveRepository o,
            ProgramRepository g, ProjectRepository j, CycleAuditLogRepository a) {
        cycles = c;
        plans = p;
        objectives = o;
        programs = g;
        projects = j;
        audit = a;
    }

    public Response create(Create r, String user) {
        validatePeriod(r.startYear(), r.endYear());
        if (cycles.existsByNameIgnoreCaseAndStartYearAndEndYearAndDeletedAtIsNull(r.name(), r.startYear(), r.endYear()))
            conflict("CYCLE_DUPLICATE", "Cycle already exists", Map.of());
        StrategicPlan c = new StrategicPlan();
        c.setName(r.name().trim());
        c.setStartYear(r.startYear());
        c.setEndYear(r.endYear());
        c.setDescription(r.description());
        c.setStatus(StrategicPlanStatus.DRAFT);
        c.setRevision(1);
        c.setCreatedBy(user);
        c.setUpdatedBy(user);
        c = cycles.save(c);
        log(c, "CREATED", null, c.getStatus(), null, user);
        return map(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<Response> list(int page, int size, String search, StrategicPlanStatus status, Integer sy,
            Integer ey, String sort, String order) {
        Set<String> allowed = Set.of("name", "startYear", "endYear", "status", "createdAt");
        if (!allowed.contains(sort))
            throw domain(HttpStatus.BAD_REQUEST, "CYCLE_INVALID_SORT", "Invalid sort field", Map.of("sort", sort));
        Specification<StrategicPlan> filters = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        String normalizedSearch = blank(search);
        if (normalizedSearch != null) {
            String pattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
            filters = filters.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        if (status != null)
            filters = filters.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (sy != null)
            filters = filters.and((root, query, cb) -> cb.equal(root.get("startYear"), sy));
        if (ey != null)
            filters = filters.and((root, query, cb) -> cb.equal(root.get("endYear"), ey));
        Page<StrategicPlan> p = cycles.findAll(filters,
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(order), sort)));
        return new PageResponse<>(p.map(this::map).getContent(), page, size, p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return map(entity(id));
    }

    @Transactional(readOnly = true)
    public Response active() {
        return map(cycles.findFirstByStatus(StrategicPlanStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active cycle")));
    }

    public Response patch(UUID id, Patch r, String user) {
        StrategicPlan c = entity(id);
        if (r.version() != null && !r.version().equals(c.getVersion()))
            conflict("CYCLE_VERSION_CONFLICT", "Cycle was modified", Map.of("version", c.getVersion()));
        if (Set.of(StrategicPlanStatus.ACTIVE, StrategicPlanStatus.REPLACED, StrategicPlanStatus.CLOSED)
                .contains(c.getStatus()))
            conflict("CYCLE_NOT_EDITABLE", "Cycle is not editable", Map.of("status", c.getStatus()));
        if ((r.startYear() != null || r.endYear() != null) && c.getStatus() != StrategicPlanStatus.DRAFT)
            conflict("CYCLE_PERIOD_LOCKED", "Approved/review cycle period is locked", Map.of());
        int sy = r.startYear() == null ? c.getStartYear() : r.startYear(),
                ey = r.endYear() == null ? c.getEndYear() : r.endYear();
        validatePeriod(sy, ey);
        if (r.name() != null)
            c.setName(r.name().trim());
        if (r.description() != null)
            c.setDescription(r.description());
        c.setStartYear(sy);
        c.setEndYear(ey);
        c.setUpdatedBy(user);
        return map(cycles.save(c));
    }

    public void delete(UUID id, String user) {
        StrategicPlan c = entity(id);
        Map<String, Long> d = dependencyCounts(id);
        if (c.getStatus() != StrategicPlanStatus.DRAFT || d.values().stream().anyMatch(v -> v > 0))
            conflict("CYCLE_DELETE_BLOCKED", "Only dependency-free draft cycles can be deleted", d);
        c.setDeletedAt(Instant.now());
        c.setUpdatedBy(user);
        log(c, "DELETED", c.getStatus(), c.getStatus(), null, user);
    }

    public Response transition(UUID id, String action, String comment, String user) {
        StrategicPlan c = entity(id);
        StrategicPlanStatus from = c.getStatus(), to = switch (action) {
            case "submit-review" -> StrategicPlanStatus.IN_REVIEW;
            case "approve" -> StrategicPlanStatus.APPROVED;
            case "activate" -> StrategicPlanStatus.ACTIVE;
            case "reject" -> StrategicPlanStatus.DRAFT;
            case "close" -> StrategicPlanStatus.CLOSED;
            default -> throw domain(HttpStatus.BAD_REQUEST, "CYCLE_ACTION_INVALID", "Invalid action", Map.of());
        };
        boolean ok = (from == StrategicPlanStatus.DRAFT && to == StrategicPlanStatus.IN_REVIEW)
                || (from == StrategicPlanStatus.IN_REVIEW
                        && (to == StrategicPlanStatus.APPROVED || to == StrategicPlanStatus.DRAFT))
                || (from == StrategicPlanStatus.APPROVED && to == StrategicPlanStatus.ACTIVE)
                || (from == StrategicPlanStatus.ACTIVE && to == StrategicPlanStatus.CLOSED);
        if (!ok)
            conflict("CYCLE_INVALID_TRANSITION", "Invalid cycle transition",
                    Map.of("currentStatus", from, "requestedStatus", to));
        if (action.equals("activate"))
            cycles.findFirstByStatus(StrategicPlanStatus.ACTIVE).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
                StrategicPlanStatus old = x.getStatus();
                x.setStatus(StrategicPlanStatus.REPLACED);
                x.setUpdatedBy(user);
                log(x, "REPLACED", old, x.getStatus(), "Replaced by " + id, user);
            });
        c.setStatus(to);
        c.setUpdatedBy(user);
        if (to == StrategicPlanStatus.APPROVED) {
            c.setApprovedBy(user);
            c.setApprovalDate(LocalDate.now());
        }
        log(c, action.toUpperCase(Locale.ROOT), from, to, comment, user);
        return map(cycles.save(c));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID id) {
        StrategicPlan c = entity(id);
        List<OperationalPlan> ps = plans.findByStrategicPlan_Id(id);
        List<Project> pj = ps.stream().flatMap(x -> projects.findByOperationalPlan_Id(x.getId()).stream()).toList();
        BigDecimal budget = pj.stream().map(Project::getBudget).filter(Objects::nonNull).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        pj.forEach(x -> byStatus.merge(x.getStatus().name(), 1L, Long::sum));
        return Map.ofEntries(Map.entry("cycle", map(c)), Map.entry("operationalPlans", ps.size()),
                Map.entry("objectives", objectives.findByStrategicPlan_Id(id).size()),
            Map.entry("programs", programs.findByStrategicObjectives_StrategicPlan_Id(id).size()),
                Map.entry("demands", 0), Map.entry("projects", pj.size()), Map.entry("totalBudget", budget),
                Map.entry("consumedBudget", BigDecimal.ZERO), Map.entry("aggregateProgress", 0),
                Map.entry("projectsByStatus", byStatus),
                Map.entry("approval",
                        Map.of("date", Optional.ofNullable(c.getApprovalDate()).map(Object::toString).orElse(""),
                                "approvedBy", Optional.ofNullable(c.getApprovedBy()).orElse(""))));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> dependencyCounts(UUID id) {
        long pl = plans.findByStrategicPlan_Id(id).size(), ob = objectives.findByStrategicPlan_Id(id).size(),
                pr = programs.findByStrategicObjectives_StrategicPlan_Id(id).size(),
                pj = plans.findByStrategicPlan_Id(id).stream()
                        .mapToLong(x -> projects.findByOperationalPlan_Id(x.getId()).size()).sum();
        return Map.of("plans", pl, "objectives", ob, "programs", pr, "projects", pj);
    }

    public StrategicPlan entity(UUID id) {
        return cycles.findById(id).filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle not found: " + id));
    }

    private void validatePeriod(int s, int e) {
        if (e != s + 2)
            throw domain(HttpStatus.UNPROCESSABLE_ENTITY, "CYCLE_INVALID_PERIOD", "Rolling cycle must have three years",
                    Map.of("expectedEndYear", s + 2));
    }

    private void log(StrategicPlan c, String a, StrategicPlanStatus f, StrategicPlanStatus t, String m, String u) {
        CycleAuditLog l = new CycleAuditLog();
        l.setCycle(c);
        l.setAction(a);
        l.setFromStatus(f == null ? null : f.name());
        l.setToStatus(t == null ? null : t.name());
        l.setComment(m);
        l.setPerformedBy(u);
        audit.save(l);
    }

    private Response map(StrategicPlan c) {
        return new Response(c.getId(), c.getName(), c.getStartYear(), c.getEndYear(), c.getStatus(), c.getRevision(),
                c.getApprovalDate(), c.getApprovedBy(), c.getDescription(), c.getCreatedAt(), c.getCreatedBy(),
                c.getUpdatedAt(), c.getUpdatedBy(), c.getVersion());
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private DomainException domain(HttpStatus s, String c, String m, Map<String, Object> d) {
        return new DomainException(s, c, m, d);
    }

    private void conflict(String c, String m, Map d) {
        throw domain(HttpStatus.CONFLICT, c, m, d);
    }

    public OperationalPlanRepository plans() {
        return plans;
    }

    public StrategicObjectiveRepository objectives() {
        return objectives;
    }

    public ProgramRepository programs() {
        return programs;
    }

    public ProjectRepository projects() {
        return projects;
    }

    public CycleAuditLogRepository audit() {
        return audit;
    }
}
