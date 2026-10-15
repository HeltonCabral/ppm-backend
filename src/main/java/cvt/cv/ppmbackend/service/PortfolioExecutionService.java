package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PortfolioExecutionDtos.Group;
import cvt.cv.ppmbackend.dto.PortfolioExecutionDtos.Response;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PortfolioExecutionService {
    private final ProjectRepository projects;
    private final ProjectGuard guard;

    public PortfolioExecutionService(ProjectRepository projects, ProjectGuard guard) {
        this.projects = projects;
        this.guard = guard;
    }

    public Response execution() {
        List<Project> visible = projects.findAll().stream().filter(guard::canView).toList();
        Map<String, List<Project>> directions = visible.stream().collect(Collectors.groupingBy(
                p -> Objects.requireNonNullElse(p.getDirectionCode(), ""), LinkedHashMap::new, Collectors.toList()));
        Map<String, List<Project>> programs = visible.stream().collect(Collectors.groupingBy(
                p -> p.getProgram() == null ? "" : p.getProgram().getId().toString(),
                LinkedHashMap::new, Collectors.toList()));
        List<Group> byDirection = directions.values().stream().map(rows -> group(rows, null,
                rows.get(0).getDirectionCode(), Objects.requireNonNullElse(rows.get(0).getDirectionName(), "Sem direção")))
                .sorted(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER)).toList();
        List<Group> byProgram = programs.values().stream().map(rows -> {
            Project sample = rows.get(0);
            return group(rows, sample.getProgram() == null ? null : sample.getProgram().getId(), null,
                    sample.getProgram() == null ? "Sem programa" : sample.getProgram().getName());
        }).sorted(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return new Response(byDirection, byProgram);
    }

    private static Group group(List<Project> rows, UUID id, String code, String name) {
        long green = count(rows, p -> p.getLastReportStatus() == ExecutiveStatus.GREEN);
        long yellow = count(rows, p -> p.getLastReportStatus() == ExecutiveStatus.YELLOW);
        long red = count(rows, p -> p.getLastReportStatus() == ExecutiveStatus.RED);
        long noReport = count(rows, p -> p.getLastReportStatus() == null);
        double average = BigDecimal.valueOf(rows.stream().map(Project::getExecution).filter(Objects::nonNull)
                .mapToInt(e -> Objects.requireNonNullElse(e.getProgress(), 0)).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
        BigDecimal budget = rows.stream().map(Project::getBudget).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumed = rows.stream().map(Project::getExecution).filter(Objects::nonNull)
                .map(e -> e.getConsumedBudget()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        long slipped = count(rows, p -> p.getStatus() != ProjectStatus.COMPLETED
                && p.getStatus() != ProjectStatus.CANCELLED && p.getExecution() != null
                && p.getExecution().getPlannedEndDate() != null && p.getExecution().getPlannedEndDate().isBefore(today));
        Instant staleBefore = Instant.now().minus(30, ChronoUnit.DAYS);
        long stale = count(rows, p -> p.getStatus() == ProjectStatus.IN_PROGRESS
                && (p.getLastReportAt() == null || p.getLastReportAt().isBefore(staleBefore)));
        return new Group(id, code, name, rows.size(), green, yellow, red, noReport, average, budget, consumed,
                slipped, stale);
    }

    private static long count(List<Project> rows, Function<Project, Boolean> predicate) {
        return rows.stream().filter(p -> Boolean.TRUE.equals(predicate.apply(p))).count();
    }
}
