package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.enums.ProjectPhaseStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cálculos do plano por fases, sem dependências: origem das fases, distribuição das datas previstas pelo
 * peso, progresso ponderado e fase atual.
 */
public final class ProjectPhasePlanner {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ProjectPhasePlanner() {
    }

    public record PhaseSeed(String name, int sequence, BigDecimal weight) {
    }

    /** Fases ativas do planeamento da Demanda: o personalizado, se existir; senão as do Tipo. */
    public static List<PhaseSeed> seedsFromDemand(Demand demand) {
        if (demand == null) {
            return List.of();
        }
        List<DemandCustomPhase> custom = demand.getCustomPhases() == null ? List.of() : demand.getCustomPhases();
        List<PhaseSeed> fromCustom = custom.stream()
                .filter(DemandCustomPhase::isActive)
                .sorted(Comparator.comparing(DemandCustomPhase::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new PhaseSeed(p.getName(), p.getSequence() == null ? 0 : p.getSequence(),
                        p.getDurationWeightPercentage()))
                .toList();
        return !fromCustom.isEmpty() ? fromCustom : seedsFromModel(demand.getType());
    }

    public static List<PhaseSeed> seedsFromModel(PhaseModel model) {
        if (model == null || model.getPhases() == null) {
            return List.of();
        }
        return model.getPhases().stream()
                .filter(PhaseDefinition::isActive)
                .sorted(Comparator.comparing(PhaseDefinition::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new PhaseSeed(p.getName(), p.getSequence() == null ? 0 : p.getSequence(),
                        p.getDurationWeightPercentage()))
                .toList();
    }

    /** Cria as fases do projeto e distribui-as entre o início e o fim previstos da execução. */
    public static List<ProjectPhase> build(Project project, List<PhaseSeed> seeds, String actor) {
        List<ProjectPhase> phases = new ArrayList<>();
        for (PhaseSeed seed : seeds) {
            ProjectPhase phase = new ProjectPhase();
            phase.setProject(project);
            phase.setName(seed.name().length() > 150 ? seed.name().substring(0, 150) : seed.name());
            phase.setSequence(seed.sequence());
            phase.setWeight((seed.weight() == null ? BigDecimal.ZERO : seed.weight()).setScale(2, RoundingMode.HALF_UP));
            phase.setStatus(ProjectPhaseStatus.NOT_STARTED);
            phase.setProgress(0);
            phase.setCreatedBy(actor);
            phase.setUpdatedBy(actor);
            phases.add(phase);
        }
        distribute(phases,
                project.getExecution() == null ? null : project.getExecution().getPlannedStartDate(),
                project.getExecution() == null ? null : project.getExecution().getPlannedEndDate());
        return phases;
    }

    /**
     * Datas previstas pelo peso entre {@code start} e {@code end}: a fase i começa no dia a seguir ao fim da
     * fase i-1 e a última termina em {@code end}. Sem datas válidas, ficam a null.
     */
    public static void distribute(List<ProjectPhase> phases, LocalDate start, LocalDate end) {
        if (phases.isEmpty()) {
            return;
        }
        if (start == null || end == null || end.isBefore(start)) {
            phases.forEach(p -> {
                p.setPlannedStartDate(null);
                p.setPlannedEndDate(null);
            });
            return;
        }
        BigDecimal totalWeight = phases.stream().map(ProjectPhasePlanner::weightOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean equalSplit = totalWeight.signum() == 0;
        if (equalSplit) {
            totalWeight = BigDecimal.valueOf(phases.size());
        }
        BigDecimal totalDays = BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
        BigDecimal cumulative = BigDecimal.ZERO;
        LocalDate previousEnd = start.minusDays(1);
        for (int i = 0; i < phases.size(); i++) {
            ProjectPhase phase = phases.get(i);
            cumulative = cumulative.add(equalSplit ? BigDecimal.ONE : weightOf(phase));
            LocalDate phaseStart = min(previousEnd.plusDays(1), end);
            LocalDate phaseEnd;
            if (i == phases.size() - 1) {
                phaseEnd = end;
            } else {
                long endOffset = cumulative.multiply(totalDays).divide(totalWeight, 0, RoundingMode.HALF_UP)
                        .longValue() - 1;
                phaseEnd = min(max(start.plusDays(endOffset), phaseStart), end);
            }
            phase.setPlannedStartDate(phaseStart);
            phase.setPlannedEndDate(phaseEnd);
            previousEnd = phaseEnd;
        }
    }

    /** Σ(peso × (DONE ? 100 : progresso)) / Σ pesos, sem as SKIPPED. {@code null} sem fases a contar. */
    public static Integer weightedProgress(List<ProjectPhase> phases) {
        List<ProjectPhase> counted = phases.stream().filter(p -> p.getStatus() != ProjectPhaseStatus.SKIPPED).toList();
        if (counted.isEmpty()) {
            return null;
        }
        BigDecimal totalWeight = counted.stream().map(ProjectPhasePlanner::weightOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean equalSplit = totalWeight.signum() == 0;
        BigDecimal sum = BigDecimal.ZERO;
        for (ProjectPhase phase : counted) {
            BigDecimal done = phase.getStatus() == ProjectPhaseStatus.DONE ? HUNDRED
                    : BigDecimal.valueOf(phase.getProgress() == null ? 0 : phase.getProgress());
            sum = sum.add((equalSplit ? BigDecimal.ONE : weightOf(phase)).multiply(done));
        }
        BigDecimal divisor = equalSplit ? BigDecimal.valueOf(counted.size()) : totalWeight;
        return sum.divide(divisor, 0, RoundingMode.HALF_UP).intValue();
    }

    /** Nome da primeira fase IN_PROGRESS, pela sequência. */
    public static String currentPhaseName(List<ProjectPhase> phases) {
        return phases.stream()
                .filter(p -> p.getStatus() == ProjectPhaseStatus.IN_PROGRESS)
                .min(Comparator.comparing(ProjectPhase::getSequence))
                .map(ProjectPhase::getName)
                .orElse(null);
    }

    private static BigDecimal weightOf(ProjectPhase phase) {
        return phase.getWeight() == null ? BigDecimal.ZERO : phase.getWeight();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? b : a;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? b : a;
    }
}
