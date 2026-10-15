package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CapacityPlanningDtos.*;
import cvt.cv.ppmbackend.entity.PersonCapacity;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.PersonCapacityRepository;
import cvt.cv.ppmbackend.repository.ProjectPhaseRepository;
import cvt.cv.ppmbackend.repository.ProjectProfileRepository;
import cvt.cv.ppmbackend.repository.ProjectTeamMemberRepository;
import cvt.cv.ppmbackend.service.CapacityMath.Assignment;
import cvt.cv.ppmbackend.service.CapacityMath.Range;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static cvt.cv.ppmbackend.service.CapacityMath.*;

/**
 * Capacidade das pessoas e dos perfis. Só os projetos PLANNED, IN_PROGRESS e ON_HOLD ocupam pessoas. Uma pessoa
 * é a mesma entre projetos quando é INTERNAL e tem o mesmo {@code code} (sem distinguir maiúsculas).
 */
@Service
@Transactional(readOnly = true)
public class ProjectCapacityService {
    public static final Set<ProjectStatus> OCCUPYING =
            EnumSet.of(ProjectStatus.PLANNED, ProjectStatus.IN_PROGRESS, ProjectStatus.ON_HOLD);
    public static final Set<ProjectStatus> CONCURRENT =
            EnumSet.of(ProjectStatus.PLANNED, ProjectStatus.IN_PROGRESS);
    static final String HIDDEN_PROJECT = "Outro projeto";

    private final ProjectGuard guard;
    private final ProjectTeamMemberRepository members;
    private final ProjectProfileRepository projectProfiles;
    private final ProjectPhaseRepository phases;
    private final PersonCapacityRepository personCapacities;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CapacitySettingsService settings;

    public ProjectCapacityService(ProjectGuard guard, ProjectTeamMemberRepository members,
            ProjectProfileRepository projectProfiles, ProjectPhaseRepository phases,
            PersonCapacityRepository personCapacities) {
        this.guard = guard;
        this.members = members;
        this.projectProfiles = projectProfiles;
        this.phases = phases;
        this.personCapacities = personCapacities;
    }

    /** Registos de capacidade individual por pessoa ({@code userName} em minúsculas). */
    private Map<String, List<PersonCapacity>> capacitiesOf(Collection<String> personKeys) {
        List<String> keys = personKeys.stream().filter(Objects::nonNull).distinct().toList();
        return keys.isEmpty() ? Map.of() : personCapacities.findByUserNameIn(keys).stream()
                .collect(Collectors.groupingBy(c -> c.getUserName().toLowerCase(Locale.ROOT)));
    }

    private static List<Integer> monthlyCapacity(List<YearMonth> months, List<PersonCapacity> records) {
        return months.stream().map(m -> PersonCapacityRules.inMonth(m, records).percentage()).toList();
    }

    private static String reasonToday(List<PersonCapacity> records) {
        return PersonCapacityRules.onDay(LocalDate.now(), records).reasonLabel();
    }

    // ----- GET /projects/{id}/capacity -----

    public ProjectCapacityResponse projectCapacity(UUID projectId, LocalDate from, LocalDate to) {
        Project project = guard.require(projectId);
        Visibility visibility = new Visibility();
        if (!visibility.canSee(project)) {
            throw new ResourceNotFoundException("Projeto não encontrado: " + projectId);
        }
        Range projectRange = projectRange(project);
        List<YearMonth> months = months(from != null ? from : projectRange.start(), to != null ? to : projectRange.end());

        List<ProjectTeamMember> own = members.findByProjectId(projectId);
        List<ProjectTeamMember> others = members.findInProjectsWithStatus(OCCUPYING).stream()
                .filter(m -> !m.getProject().getId().equals(projectId) && personKey(m) != null)
                .toList();
        PhaseLookup phaseLookup = new PhaseLookup(own, others, List.of(project));
        List<Assignment> ownAssignments = own.stream().map(m -> assignment(m, phaseLookup.of(m.getProject()))).toList();
        Map<String, List<Assignment>> byPerson = new HashMap<>();
        for (Assignment a : ownAssignments) {
            String key = personKey(a.member());
            if (key != null) byPerson.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        for (ProjectTeamMember m : others) {
            byPerson.computeIfAbsent(personKey(m), k -> new ArrayList<>()).add(assignment(m, phaseLookup.of(m.getProject())));
        }

        Map<String, List<PersonCapacity>> capacities = capacitiesOf(byPerson.keySet());
        List<MemberCapacity> memberRows = ownAssignments.stream()
                .sorted(Comparator.comparing(a -> a.member().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(a -> {
                    String key = personKey(a.member());
                    return memberRow(a, key == null ? List.of(a) : byPerson.get(key), months, projectId, visibility,
                            key == null ? List.of() : capacities.getOrDefault(key, List.of()));
                })
                .toList();

        List<ProjectProfile> ownProfiles = projectProfiles.findByProjectIdOrderByProfileNameAsc(projectId);
        Map<UUID, ProjectProfile> portfolioProfiles = new LinkedHashMap<>();
        projectProfiles.findInProjectsWithStatus(OCCUPYING).stream()
                .filter(pp -> guard.canView(pp.getProject()))
                .forEach(pp -> portfolioProfiles.put(pp.getId(), pp));
        ownProfiles.forEach(pp -> portfolioProfiles.put(pp.getId(), pp));
        PhaseLookup profilePhases = new PhaseLookup(List.of(), List.of(),
                portfolioProfiles.values().stream().map(ProjectProfile::getProject).toList());

        List<ProfileCapacity> profileRows = new ArrayList<>();
        for (ProjectProfile pp : ownProfiles) {
            List<Double> required = profileRequired(pp, project, phaseLookup.of(project), months);
            List<Assignment> filling = ownAssignments.stream()
                    .filter(a -> a.member().getProjectProfile() != null
                            && a.member().getProjectProfile().getId().equals(pp.getId()))
                    .toList();
            List<Double> assigned = months.stream()
                    .map(m -> fte(filling.stream().mapToDouble(a -> a.percent() / 100.0 * coverage(m, List.of(a.period())))
                            .sum()))
                    .toList();
            UUID capacityProfileId = pp.getProfile().getId();
            List<ProjectProfile> sameProfile = portfolioProfiles.values().stream()
                    .filter(other -> other.getProfile().getId().equals(capacityProfileId))
                    .toList();
            List<Double> portfolioLoad = new ArrayList<>();
            for (int i = 0; i < months.size(); i++) {
                YearMonth m = months.get(i);
                portfolioLoad.add(fte(sameProfile.stream()
                        .mapToDouble(other -> quantity(other) * coverage(m,
                                profileRanges(other, other.getProject(), profilePhases.of(other.getProject()))))
                        .sum()));
            }
            Profile profile = pp.getProfile();
            profileRows.add(new ProfileCapacity(pp.getId(), profile.getId(), pp.getProfileName(), pp.getDirectionCode(),
                    required, assigned, portfolioLoad, profile.getAvailableCapacity()));
        }
        return new ProjectCapacityResponse(labels(months), memberRows, profileRows);
    }

    private MemberCapacity memberRow(Assignment a, List<Assignment> person, List<YearMonth> months, UUID projectId,
            Visibility visibility, List<PersonCapacity> capacityRecords) {
        List<Integer> thisProject = new ArrayList<>();
        List<Integer> total = new ArrayList<>();
        List<List<ProjectShare>> breakdown = new ArrayList<>();
        for (YearMonth m : months) {
            thisProject.add(a.period().overlaps(m) ? a.percent() : 0);
            total.add(dailyPeak(m, person));
            breakdown.add(shares(m, person, projectId, visibility));
        }
        ProjectTeamMember member = a.member();
        return new MemberCapacity(member.getId(), member.getName(), member.getCode(),
                member.getProjectProfile() == null ? null : member.getProjectProfile().getProfileName(),
                member.getProjectProfile() == null ? null : member.getProjectProfile().getId(), a.percent(),
                a.period().start(), a.period().end(), a.assumed(), thisProject, total, breakdown,
                total.stream().mapToInt(Integer::intValue).max().orElse(0),
                monthlyCapacity(months, capacityRecords), reasonToday(capacityRecords),
                months.stream().map(m -> concurrentPeak(m, person)).toList(),
                PersonCapacityRules.maxConcurrentOnDay(LocalDate.now(), capacityRecords, defaultMax()));
    }

    /** Cada projeto ativo no mês, com a soma das alocações dessa pessoa nele. */
    private static List<ProjectShare> shares(YearMonth month, List<Assignment> person, UUID projectId,
            Visibility visibility) {
        Map<UUID, Integer> percentByProject = new LinkedHashMap<>();
        Map<UUID, Project> projectsById = new HashMap<>();
        for (Assignment a : person) {
            if (a.period().overlaps(month)) {
                percentByProject.merge(a.project().getId(), a.percent(), Integer::sum);
                projectsById.put(a.project().getId(), a.project());
            }
        }
        return percentByProject.entrySet().stream()
                .map(e -> visibility.share(projectsById.get(e.getKey()), e.getValue(), e.getKey().equals(projectId)))
                .toList();
    }

    private static List<Double> profileRequired(ProjectProfile pp, Project project, List<ProjectPhase> projectPhases,
            List<YearMonth> months) {
        List<Range> ranges = profileRanges(pp, project, projectPhases);
        return months.stream().map(m -> fte(quantity(pp) * coverage(m, ranges))).toList();
    }

    // ----- GET /capacity/people/{username} -----

    public PersonLoadResponse person(String username, LocalDate from, LocalDate to, UUID excludeProjectId) {
        List<YearMonth> months = requireWindow(from, to);
        if (username == null || username.isBlank()) {
            throw new BadRequestException("username é obrigatório.");
        }
        List<ProjectTeamMember> rows = members.findPersonAssignments(username.trim(), OCCUPYING).stream()
                .filter(m -> excludeProjectId == null || !m.getProject().getId().equals(excludeProjectId))
                .toList();
        PhaseLookup phaseLookup = new PhaseLookup(rows, List.of(), List.of());
        List<Assignment> assignments = rows.stream().map(m -> assignment(m, phaseLookup.of(m.getProject()))).toList();
        Range window = new Range(from, to);
        Visibility visibility = new Visibility();
        List<PersonAssignment> listed = assignments.stream()
                .filter(a -> !a.period().isComplete() || overlaps(a.period(), window))
                .sorted(Comparator.comparing(a -> a.period().start(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> {
                    boolean visible = visibility.canSee(a.project());
                    return new PersonAssignment(visible ? a.project().getId() : null,
                            visible ? a.project().getCode() : null, visible ? a.project().getName() : HIDDEN_PROJECT,
                            a.percent(), a.period().start(), a.period().end(), a.assumed());
                })
                .toList();
        List<PersonCapacity> records = capacitiesOf(List.of(username.trim().toLowerCase(Locale.ROOT)))
                .getOrDefault(username.trim().toLowerCase(Locale.ROOT), List.of());
        return new PersonLoadResponse(labels(months), months.stream().map(m -> dailyPeak(m, assignments)).toList(),
                listed, monthlyCapacity(months, records), reasonToday(records),
                months.stream().map(m -> concurrentPeak(m, assignments)).toList(),
                PersonCapacityRules.maxConcurrentOnDay(LocalDate.now(), records, defaultMax()));
    }

    // ----- Avisos ao gravar a equipa -----

    /** Sobrealocação (pico diário > 100%) e período fora do projeto. Não recusa nada: a decisão é do gestor. */
    @Transactional
    public List<String> warningsFor(ProjectTeamMember member) {
        List<String> warnings = new ArrayList<>();
        Project project = member.getProject();
        Range projectRange = projectRange(project);
        if (member.getStartDate() != null && projectRange.start() != null
                && member.getStartDate().isBefore(projectRange.start())) {
            warnings.add("O período da alocação começa antes do início do projeto.");
        }
        if (member.getEndDate() != null && projectRange.end() != null
                && member.getEndDate().isAfter(projectRange.end())) {
            warnings.add("O período da alocação termina depois do fim do projeto.");
        }
        String key = personKey(member);
        if (key == null) {
            return warnings;
        }
        List<ProjectTeamMember> rows = new ArrayList<>(members.findPersonAssignments(key, OCCUPYING).stream()
                .filter(m -> !m.getId().equals(member.getId()))
                .toList());
        PhaseLookup phaseLookup = new PhaseLookup(rows, List.of(member), List.of(project));
        Assignment own = assignment(member, phaseLookup.of(project));
        if (!own.period().isComplete()) {
            return warnings;
        }
        List<Assignment> others = rows.stream().map(m -> assignment(m, phaseLookup.of(m.getProject()))).toList();
        List<Assignment> all = new ArrayList<>(others);
        all.add(own);
        List<PersonCapacity> records = capacitiesOf(List.of(key)).getOrDefault(key, List.of());
        Visibility visibility = new Visibility();
        for (YearMonth month : months(own.period().start(), own.period().end())) {
            int peak = dailyPeak(month, all);
            PersonCapacityRules.Effective limit = PersonCapacityRules.inMonth(month, records);
            if (peak > limit.percentage()) {
                if (limit.reason() != null) {
                    warnings.add(member.getName() + " fica a " + peak + "% em " + label(month)
                            + ", acima da sua capacidade de " + limit.percentage() + "% (" + limit.reasonLabel() + ").");
                } else {
                    String elsewhere = shares(month, others, project.getId(), visibility).stream()
                            .map(s -> (s.projectCode() != null ? s.projectCode() : s.projectName()) + " " + s.percent() + "%")
                            .collect(Collectors.joining(", "));
                    warnings.add(member.getName() + " fica a " + peak + "% em " + label(month)
                            + (elsewhere.isEmpty() ? "" : " (" + elsewhere + ")") + ".");
                }
            }
            int concurrent = concurrentPeak(month, all);
            int projectLimit = PersonCapacityRules.maxConcurrentInMonth(month, records, defaultMax());
            if (concurrent > projectLimit) {
                warnings.add(member.getName() + " fica em " + concurrent + " projetos ao mesmo tempo em "
                        + label(month) + " (limite " + projectLimit + ").");
            }
        }
        return warnings;
    }

    // ----- Portefólio -----

    /** {@code GET /capacity/profiles}: por perfil de capacidade, o pedido e o alocado nos projetos abertos. */
    public PortfolioProfilesResponse portfolioProfiles(LocalDate from, LocalDate to) {
        List<YearMonth> months = requireWindow(from, to);
        List<ProjectProfile> pps = projectProfiles.findInProjectsWithStatus(OCCUPYING);
        pps = pps.stream().filter(pp -> guard.canView(pp.getProject())).toList();
        List<ProjectTeamMember> filling = members.findInProjectsWithStatus(OCCUPYING).stream()
                .filter(m -> m.getProjectProfile() != null && guard.canView(m.getProject()))
                .toList();
        PhaseLookup phaseLookup = new PhaseLookup(filling, List.of(), pps.stream().map(ProjectProfile::getProject).toList());
        List<Assignment> assignments = filling.stream().map(m -> assignment(m, phaseLookup.of(m.getProject()))).toList();
        Visibility visibility = new Visibility();

        Map<UUID, List<ProjectProfile>> byProfile = pps.stream()
                .collect(Collectors.groupingBy(pp -> pp.getProfile().getId(), LinkedHashMap::new, Collectors.toList()));
        List<PortfolioProfile> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<ProjectProfile>> entry : byProfile.entrySet()) {
            Profile profile = entry.getValue().get(0).getProfile();
            List<Double> load = new ArrayList<>();
            List<Double> assigned = new ArrayList<>();
            List<List<ProjectProfileShare>> projectsByMonth = new ArrayList<>();
            for (YearMonth m : months) {
                double monthLoad = 0;
                List<ProjectProfileShare> shares = new ArrayList<>();
                for (ProjectProfile pp : entry.getValue()) {
                    double required = quantity(pp) * coverage(m,
                            profileRanges(pp, pp.getProject(), phaseLookup.of(pp.getProject())));
                    if (required > 0) {
                        monthLoad += required;
                        boolean visible = visibility.canSee(pp.getProject());
                        shares.add(new ProjectProfileShare(visible ? pp.getProject().getId() : null,
                                visible ? pp.getProject().getCode() : null,
                                visible ? pp.getProject().getName() : HIDDEN_PROJECT, fte(required)));
                    }
                }
                load.add(fte(monthLoad));
                assigned.add(fte(assignments.stream()
                        .filter(a -> a.member().getProjectProfile().getProfile().getId().equals(entry.getKey()))
                        .mapToDouble(a -> a.percent() / 100.0 * coverage(m, List.of(a.period())))
                        .sum()));
                projectsByMonth.add(shares);
            }
            rows.add(new PortfolioProfile(profile.getId(), profile.getName(), profile.getDirectionCode(),
                    profile.getAvailableCapacity(), load, assigned, projectsByMonth));
        }
        rows.sort(Comparator.comparing(PortfolioProfile::profileName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new PortfolioProfilesResponse(labels(months), rows);
    }

    /** {@code GET /capacity/people}: carga total de cada pessoa interna, por mês (pico diário). */
    public PortfolioPeopleResponse portfolioPeople(LocalDate from, LocalDate to, String directionCode) {
        List<YearMonth> months = requireWindow(from, to);
        List<ProjectTeamMember> rows = members.findInProjectsWithStatus(OCCUPYING).stream()
                .filter(m -> personKey(m) != null && guard.canView(m.getProject()))
                .toList();
        PhaseLookup phaseLookup = new PhaseLookup(rows, List.of(), List.of());
        Map<String, List<Assignment>> byPerson = new LinkedHashMap<>();
        rows.forEach(m -> byPerson.computeIfAbsent(personKey(m), k -> new ArrayList<>())
                .add(assignment(m, phaseLookup.of(m.getProject()))));
        String direction = directionCode == null || directionCode.isBlank() ? null : directionCode.trim();
        Map<String, List<PersonCapacity>> capacities = capacitiesOf(byPerson.keySet());
        List<PortfolioPerson> people = new ArrayList<>();
        for (Map.Entry<String, List<Assignment>> entry : byPerson.entrySet()) {
            List<Assignment> person = entry.getValue();
            String personDirection = person.stream().map(a -> a.member().getDirectionCode())
                    .filter(Objects::nonNull).findFirst().orElse(null);
            if (direction != null && !direction.equalsIgnoreCase(personDirection)) {
                continue;
            }
            List<Integer> load = months.stream().map(m -> dailyPeak(m, person)).toList();
            List<PersonCapacity> records = capacities.getOrDefault(entry.getKey(), List.of());
            people.add(new PortfolioPerson(entry.getKey(), person.get(0).member().getName(), personDirection, load,
                    load.stream().mapToInt(Integer::intValue).max().orElse(0),
                    monthlyCapacity(months, records), months.stream().map(m -> concurrentPeak(m, person)).toList(),
                    PersonCapacityRules.maxConcurrentOnDay(LocalDate.now(), records, defaultMax())));
        }
        people.sort(Comparator.comparing(PortfolioPerson::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new PortfolioPeopleResponse(labels(months), people);
    }

    // ----- Apoio -----

    private static List<YearMonth> requireWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from e to são obrigatórios (AAAA-MM-DD).");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to não pode ser anterior a from.");
        }
        return months(from, to);
    }

    private static boolean overlaps(Range a, Range b) {
        return !a.end().isBefore(b.start()) && !a.start().isAfter(b.end());
    }

    private static double quantity(ProjectProfile pp) {
        return pp.getRequiredQuantity() == null ? 0 : pp.getRequiredQuantity();
    }

    private static List<String> labels(List<YearMonth> months) {
        return months.stream().map(YearMonth::toString).toList();
    }

    /** Projetos distintos ativos no dia mais carregado do mês; ON_HOLD não conta. */
    public static int concurrentPeak(YearMonth month, Collection<Assignment> assignments) {
        int peak = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            long count = assignments.stream()
                    .filter(a -> CONCURRENT.contains(a.project().getStatus()) && a.period().contains(date))
                    .map(a -> a.project().getId()).distinct().count();
            peak = Math.max(peak, Math.toIntExact(count));
        }
        return peak;
    }

    private int defaultMax() {
        return settings == null ? CapacitySettingsService.DEFAULT_MAX_CONCURRENT : settings.value();
    }

    /** Fases por projeto, carregadas numa só consulta. */
    private final class PhaseLookup {
        private final Map<UUID, List<ProjectPhase>> byProject;

        private PhaseLookup(Collection<ProjectTeamMember> a, Collection<ProjectTeamMember> b, Collection<Project> extra) {
            Set<UUID> ids = new HashSet<>();
            a.forEach(m -> ids.add(m.getProject().getId()));
            b.forEach(m -> ids.add(m.getProject().getId()));
            extra.forEach(p -> ids.add(p.getId()));
            byProject = ids.isEmpty() ? Map.of() : phases.findByProjectIdIn(ids).stream()
                    .collect(Collectors.groupingBy(p -> p.getProject().getId()));
        }

        private List<ProjectPhase> of(Project project) {
            return byProject.getOrDefault(project.getId(), List.of());
        }
    }

    /** Regra "Ver" por projeto, avaliada uma vez por pedido. */
    private final class Visibility {
        private final Map<UUID, Boolean> cache = new HashMap<>();

        private boolean canSee(Project project) {
            return cache.computeIfAbsent(project.getId(), id -> guard.canView(project));
        }

        private ProjectShare share(Project project, int percent, boolean thisProject) {
            boolean visible = thisProject || canSee(project);
            return new ProjectShare(visible ? project.getId() : null, visible ? project.getCode() : null,
                    visible ? project.getName() : HIDDEN_PROJECT, percent, thisProject);
        }
    }
}
