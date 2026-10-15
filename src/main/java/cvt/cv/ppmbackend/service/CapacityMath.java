package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cálculos de capacidade sem dependências. Pessoas: pico diário do mês (nunca a média). Perfis: FTE na
 * proporção dos dias do mês cobertos.
 */
public final class CapacityMath {
    private static final String[] MONTH_LABELS = { "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set",
            "out", "nov", "dez" };

    private CapacityMath() {
    }

    /** Intervalo de datas fechado; uma das pontas a null = sem período. */
    public record Range(LocalDate start, LocalDate end) {
        public boolean isComplete() {
            return start != null && end != null && !end.isBefore(start);
        }

        public boolean contains(LocalDate day) {
            return isComplete() && !day.isBefore(start) && !day.isAfter(end);
        }

        public boolean overlaps(YearMonth month) {
            return isComplete() && !end.isBefore(month.atDay(1)) && !start.isAfter(month.atEndOfMonth());
        }
    }

    /** Uma pessoa num projeto, com o período efetivo já resolvido. */
    public record Assignment(ProjectTeamMember member, Project project, int percent, Range period, boolean assumed) {
    }

    // ----- Períodos -----

    public static Range projectRange(Project project) {
        return project.getExecution() == null ? new Range(null, null)
                : new Range(project.getExecution().getPlannedStartDate(), project.getExecution().getPlannedEndDate());
    }

    /**
     * Intervalos das fases ligadas ao perfil (por id); sem fases ligadas com datas, o período do projeto.
     * {@code phases} (as fases do projeto) fica para compatibilidade e não é usado.
     */
    public static List<Range> profileRanges(ProjectProfile profile, Project project, List<ProjectPhase> phases) {
        List<Range> ranges = profile == null ? List.of() : profile.getPhases().stream()
                .map(p -> new Range(p.getPlannedStartDate(), p.getPlannedEndDate()))
                .filter(Range::isComplete)
                .toList();
        return ranges.isEmpty() ? List.of(projectRange(project)) : ranges;
    }

    /**
     * Período efetivo da alocação: as datas guardadas; nas que faltam, do início da primeira à última fase do
     * perfil associado, ou as datas previstas do projeto.
     */
    public static Assignment assignment(ProjectTeamMember member, List<ProjectPhase> projectPhases) {
        Project project = member.getProject();
        List<Range> fallbackRanges = profileRanges(member.getProjectProfile(), project, projectPhases);
        LocalDate fallbackStart = fallbackRanges.stream().map(Range::start).filter(Objects::nonNull)
                .min(LocalDate::compareTo).orElse(null);
        LocalDate fallbackEnd = fallbackRanges.stream().map(Range::end).filter(Objects::nonNull)
                .max(LocalDate::compareTo).orElse(null);
        boolean assumed = member.getAllocationPercentage() == null || member.getStartDate() == null
                || member.getEndDate() == null;
        int percent = member.getAllocationPercentage() == null ? 100 : member.getAllocationPercentage();
        Range period = new Range(member.getStartDate() != null ? member.getStartDate() : fallbackStart,
                member.getEndDate() != null ? member.getEndDate() : fallbackEnd);
        return new Assignment(member, project, percent, period, assumed);
    }

    // ----- Meses -----

    public static List<YearMonth> months(LocalDate from, LocalDate to) {
        List<YearMonth> months = new ArrayList<>();
        if (from == null || to == null || to.isBefore(from)) {
            return months;
        }
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    /** Fração (0–1) dos dias do mês cobertos pela união dos intervalos. */
    public static double coverage(YearMonth month, Collection<Range> ranges) {
        int covered = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            if (ranges.stream().anyMatch(r -> r.contains(date))) {
                covered++;
            }
        }
        return (double) covered / month.lengthOfMonth();
    }

    /** Maior soma diária, no mês, das alocações ativas nesse dia. */
    public static int dailyPeak(YearMonth month, Collection<Assignment> assignments) {
        int peak = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            int sum = assignments.stream().filter(a -> a.period().contains(date)).mapToInt(Assignment::percent).sum();
            peak = Math.max(peak, sum);
        }
        return peak;
    }

    public static double fte(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** "mar/27". */
    public static String label(YearMonth month) {
        return MONTH_LABELS[month.getMonthValue() - 1] + "/" + String.format("%02d", month.getYear() % 100);
    }

    /** Pessoa entre projetos: membros INTERNAL com o mesmo {@code code}, sem distinguir maiúsculas. */
    public static String personKey(ProjectTeamMember member) {
        if (member.getType() != cvt.cv.ppmbackend.enums.MemberType.INTERNAL || member.getCode() == null
                || member.getCode().isBlank()) {
            return null;
        }
        return member.getCode().trim().toLowerCase(Locale.ROOT);
    }
}
