package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.NotificationDtos.ReadRequest;
import cvt.cv.ppmbackend.dto.NotificationDtos.Response;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.*;
import cvt.cv.ppmbackend.service.CapacityMath.Assignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static cvt.cv.ppmbackend.service.CapacityMath.assignment;
import static cvt.cv.ppmbackend.service.CapacityMath.personKey;

@Service
@Transactional
public class NotificationService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationReadRepository reads;
    private final ProjectRepository projects;
    private final ProjectMilestoneRepository milestones;
    private final ProjectRiskRepository risks;
    private final ProjectChangeRequestRepository changes;
    private final ProjectPhaseGateRepository gates;
    private final ProjectDependencyRepository dependencies;
    private final ProjectTeamMemberRepository members;
    private final ProjectPhaseRepository phases;
    private final PersonCapacityRepository capacities;
    private final CapacitySettingsService settings;
    private final ProjectGuard guard;
    private final UserAccessService accessService;

    public NotificationService(NotificationReadRepository reads, ProjectRepository projects,
            ProjectMilestoneRepository milestones, ProjectRiskRepository risks,
            ProjectChangeRequestRepository changes, ProjectPhaseGateRepository gates,
            ProjectDependencyRepository dependencies, ProjectTeamMemberRepository members,
            ProjectPhaseRepository phases, PersonCapacityRepository capacities, CapacitySettingsService settings,
            ProjectGuard guard, UserAccessService accessService) {
        this.reads = reads;
        this.projects = projects;
        this.milestones = milestones;
        this.risks = risks;
        this.changes = changes;
        this.gates = gates;
        this.dependencies = dependencies;
        this.members = members;
        this.phases = phases;
        this.capacities = capacities;
        this.settings = settings;
        this.guard = guard;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        UserAccess user = accessService.current();
        boolean pmo = user.has(UserAccess.PROJECT_EDIT_ALL);
        LocalDate today = LocalDate.now();
        Instant staleBefore = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Response> alerts = new ArrayList<>();

        for (Project p : projects.findAll()) {
            if (!guard.canView(p) && !pmo) continue;
            boolean manager = manager(p, user.userName());
            if (p.getStatus() == ProjectStatus.IN_PROGRESS && (manager || pmo)
                    && (p.getLastReportAt() == null || p.getLastReportAt().isBefore(staleBefore))) {
                LocalDate at = p.getLastReportAt() == null ? today
                        : LocalDate.ofInstant(p.getLastReportAt(), ZoneOffset.UTC).plusDays(30);
                String situation = p.getLastReportAt() == null ? "NONE" : Long.toString(p.getLastReportAt().toEpochMilli());
                alerts.add(alert("REPORT_STALE:" + p.getId() + ":" + situation, "REPORT_STALE", "medium",
                        p.getName() + ": ponto de situação em falta",
                        p.getLastReportAt() == null ? "O projeto ainda não tem ponto de situação."
                                : "O último ponto de situação tem mais de 30 dias.",
                        projectHref(p, "reports"), p, at));
            }
            if (pmo && Boolean.TRUE.equals(p.getLastReportNeedsSupport())) {
                String situation = p.getLastReportAt() == null ? "NONE" : Long.toString(p.getLastReportAt().toEpochMilli());
                alerts.add(alert("PMO_SUPPORT:" + p.getId() + ":" + situation, "PMO_SUPPORT", "high",
                        p.getName() + ": apoio da PMO pedido", "O último ponto de situação pede apoio da PMO.",
                        projectHref(p, "reports"), p, date(p.getLastReportAt(), today)));
            }
        }

        for (ProjectMilestone m : milestones.findAll()) {
            Project p = m.getProject();
            if (m.getStatus() == MilestoneStatus.PENDING && m.getDueDate() != null && m.getDueDate().isBefore(today)
                    && (manager(p, user.userName()) || same(m.getOwner(), user.userName()))) {
                long days = ChronoUnit.DAYS.between(m.getDueDate(), today);
                alerts.add(alert("MILESTONE_LATE:" + m.getId() + ":" + m.getDueDate(), "MILESTONE_LATE", "medium",
                        p.getName() + ": marco atrasado", "\"" + m.getName() + "\" estava previsto para "
                                + DATE.format(m.getDueDate()) + " (" + days + " dias).",
                        projectHref(p, "plan"), p, m.getDueDate()));
            }
        }

        for (ProjectRisk risk : risks.findAll()) {
            Project p = risk.getProject();
            int score = risk.getProbability() * risk.getImpact();
            if (risk.getStatus() != ProjectRiskStatus.CLOSED && score >= 16
                    && (pmo || manager(p, user.userName()))) {
                alerts.add(alert("RISK_CRITICAL:" + risk.getId(), "RISK_CRITICAL", "critical",
                        p.getName() + ": risco crítico", risk.getTitle() + " (pontuação " + score + ").",
                        projectHref(p, "risks"), p, date(risk.getCreatedAt(), today)));
            }
        }

        if (pmo) {
            changes.findAll().stream().filter(c -> c.getStatus() == ChangeRequestStatus.PENDING).forEach(c -> {
                Project p = c.getProject();
                alerts.add(alert("CHANGE_PENDING:" + c.getId(), "CHANGE_PENDING", "medium",
                        p.getName() + ": alteração pendente", c.getTitle(), projectHref(p, "changes"), p,
                        date(c.getRequestedAt(), today)));
            });
            gates.findByStatusIn(List.of(ProjectPhaseGateStatus.REQUESTED)).forEach(g -> {
                Project p = g.getPhase().getProject();
                String situation = g.getRequestedAt() == null ? "NONE" : Long.toString(g.getRequestedAt().toEpochMilli());
                alerts.add(alert("GATE_PENDING:" + g.getPhase().getId() + ":" + situation, "GATE_PENDING", "high",
                        p.getName() + ": passagem de fase pendente", "Fase \"" + g.getPhase().getName()
                                + "\" aguarda decisão da PMO.", projectHref(p, "plan"), p,
                        date(g.getRequestedAt(), today)));
            });
        }

        addConcurrentAlerts(alerts, user, pmo, today);

        for (ProjectDependency dependency : dependencies.findAll()) {
            long days = ProjectDependencyService.conflictDays(dependency);
            if (days <= 0) continue;
            Project predecessor = dependency.getPredecessor();
            Project successor = dependency.getSuccessor();
            if (!manager(predecessor, user.userName()) && !manager(successor, user.userName())) continue;
            String situation = dependencyDates(dependency);
            alerts.add(alert("DEPENDENCY_CONFLICT:" + dependency.getId() + ":" + situation,
                    "DEPENDENCY_CONFLICT", "high", successor.getName() + ": conflito de dependência",
                    successor.getCode() + " antecipa em " + days + " dias a condição de " + predecessor.getCode() + ".",
                    projectHref(successor, "plan"), successor, today));
        }

        Set<String> readIds = reads.findByUserName(user.userName()).stream()
                .map(NotificationRead::getAlertId).collect(Collectors.toSet());
        return alerts.stream().map(a -> a.withRead(readIds.contains(a.id())))
                .sorted(Comparator.comparing(Response::at, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Response::id))
                .toList();
    }

    public void markRead(ReadRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new BadRequestException("ids é obrigatório.");
        }
        String user = accessService.current().userName();
        List<String> ids = request.ids().stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().toList();
        Set<String> existing = reads.findByUserNameAndAlertIdIn(user, ids).stream()
                .map(NotificationRead::getAlertId).collect(Collectors.toSet());
        for (String id : ids) {
            if (id.length() > 300) throw new BadRequestException("O id do alerta não pode exceder 300 caracteres.");
            if (existing.contains(id)) continue;
            NotificationRead read = new NotificationRead();
            read.setUserName(user);
            read.setAlertId(id);
            read.setReadAt(Instant.now());
            reads.save(read);
        }
    }

    private void addConcurrentAlerts(List<Response> alerts, UserAccess user, boolean pmo, LocalDate today) {
        List<ProjectTeamMember> rows = members.findInProjectsWithStatus(ProjectCapacityService.OCCUPYING).stream()
                .filter(m -> personKey(m) != null).toList();
        Set<UUID> projectIds = rows.stream().map(m -> m.getProject().getId()).collect(Collectors.toSet());
        Map<UUID, List<cvt.cv.ppmbackend.entity.ProjectPhase>> phaseMap = projectIds.isEmpty() ? Map.of()
                : phases.findByProjectIdIn(projectIds).stream()
                        .collect(Collectors.groupingBy(p -> p.getProject().getId()));
        Map<String, List<Assignment>> byPerson = new LinkedHashMap<>();
        for (ProjectTeamMember row : rows) {
            byPerson.computeIfAbsent(personKey(row), ignored -> new ArrayList<>())
                    .add(assignment(row, phaseMap.getOrDefault(row.getProject().getId(), List.of())));
        }
        List<String> keys = new ArrayList<>(byPerson.keySet());
        Map<String, List<PersonCapacity>> capacityMap = keys.isEmpty() ? Map.of()
                : capacities.findByUserNameIn(keys).stream().collect(Collectors.groupingBy(PersonCapacity::getUserName));
        YearMonth first = YearMonth.from(today);
        for (Map.Entry<String, List<Assignment>> entry : byPerson.entrySet()) {
            boolean receives = pmo || entry.getValue().stream().anyMatch(a -> manager(a.project(), user.userName()));
            if (!receives) continue;
            List<PersonCapacity> records = capacityMap.getOrDefault(entry.getKey(), List.of());
            for (int i = 0; i < 6; i++) {
                YearMonth month = first.plusMonths(i);
                int peak = ProjectCapacityService.concurrentPeak(month, entry.getValue());
                int limit = PersonCapacityRules.maxConcurrentInMonth(month, records, settings.value());
                if (peak <= limit) continue;
                String name = entry.getValue().get(0).member().getName();
                Project sample = entry.getValue().get(0).project();
                alerts.add(alert("PERSON_OVER:" + entry.getKey() + ":" + month + ":" + peak + ":" + limit,
                        "PERSON_OVER", "high", name + ": projetos em simultâneo",
                        name + " fica em " + peak + " projetos ao mesmo tempo em "
                                + CapacityMath.label(month) + " (limite " + limit + ").",
                        "/capacity?person=" + entry.getKey(), sample, month.atDay(1)));
            }
        }
    }

    private static String dependencyDates(ProjectDependency d) {
        Project p = d.getPredecessor();
        Project s = d.getSuccessor();
        String predecessor = d.getMilestone() != null ? Objects.toString(d.getMilestone().getDueDate(), "NONE")
                : p.getExecution() == null ? "NONE" : Objects.toString(p.getExecution().getPlannedEndDate(), "NONE");
        String successor = s.getExecution() == null ? "NONE" : d.getType() == ProjectDependencyType.FINISH_TO_FINISH
                ? Objects.toString(s.getExecution().getPlannedEndDate(), "NONE")
                : Objects.toString(s.getExecution().getPlannedStartDate(), "NONE");
        return predecessor + ":" + successor;
    }

    private static boolean manager(Project p, String user) { return same(p.getProjectManagerUsername(), user); }
    private static boolean same(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }
    private static String projectHref(Project p, String tab) { return "/projects/" + p.getId() + "?tab=" + tab; }
    private static LocalDate date(Instant value, LocalDate fallback) {
        return value == null ? fallback : LocalDate.ofInstant(value, ZoneOffset.UTC);
    }
    private static Response alert(String id, String kind, String severity, String title, String detail,
            String href, Project project, LocalDate at) {
        return new Response(id, kind, severity, title, detail, href, project == null ? null : project.getCode(), at, false);
    }
}
