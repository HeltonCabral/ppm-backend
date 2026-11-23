package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.enums.ProjectPhaseStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ProjectPhasePlannerTest {

    @Test
    void customPlanningWinsOverTheTypeAndInactivePhasesAreLeftOut() {
        Demand demand = new Demand();
        demand.setType(model(phase("Análise", 1, "50"), phase("Construção", 2, "50")));
        demand.getCustomPhases().add(custom("Desenho", 1, "20", true));
        demand.getCustomPhases().add(custom("Piloto", 3, "50", true));
        demand.getCustomPhases().add(custom("Desenvolvimento", 2, "30", true));
        demand.getCustomPhases().add(custom("Abandonada", 4, "10", false));

        assertThat(ProjectPhasePlanner.seedsFromDemand(demand))
                .extracting(ProjectPhasePlanner.PhaseSeed::name)
                .containsExactly("Desenho", "Desenvolvimento", "Piloto");

        Demand typeOnly = new Demand();
        typeOnly.setType(model(phase("Análise", 1, "50"), phase("Construção", 2, "50")));
        assertThat(ProjectPhasePlanner.seedsFromDemand(typeOnly))
                .extracting(ProjectPhasePlanner.PhaseSeed::name).containsExactly("Análise", "Construção");
    }

    // 1. Datas distribuídas pelo peso; cada fase começa no dia a seguir à anterior; a última acaba no fim.
    @Test
    void datesAreDistributedByWeightAndTheLastPhaseEndsOnThePlannedEnd() {
        Project project = projectBetween("2027-01-01", "2027-04-10"); // 100 dias
        List<ProjectPhase> phases = ProjectPhasePlanner.build(project, List.of(
                new ProjectPhasePlanner.PhaseSeed("Desenho", 1, new BigDecimal("20")),
                new ProjectPhasePlanner.PhaseSeed("Desenvolvimento", 2, new BigDecimal("30")),
                new ProjectPhasePlanner.PhaseSeed("Piloto", 3, new BigDecimal("50"))), "x");

        assertThat(phases).extracting(ProjectPhase::getName, ProjectPhase::getPlannedStartDate,
                        ProjectPhase::getPlannedEndDate)
                .containsExactly(
                        tuple("Desenho", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 20)),
                        tuple("Desenvolvimento", LocalDate.of(2027, 1, 21), LocalDate.of(2027, 2, 19)),
                        tuple("Piloto", LocalDate.of(2027, 2, 20), LocalDate.of(2027, 4, 10)));
        assertThat(phases).allMatch(p -> p.getStatus() == ProjectPhaseStatus.NOT_STARTED && p.getProgress() == 0);
    }

    @Test
    void withoutPlannedDatesThePhaseDatesStayEmpty() {
        Project project = new Project();
        List<ProjectPhase> phases = ProjectPhasePlanner.build(project,
                List.of(new ProjectPhasePlanner.PhaseSeed("Única", 1, BigDecimal.TEN)), "x");

        assertThat(phases.get(0).getPlannedStartDate()).isNull();
        assertThat(phases.get(0).getPlannedEndDate()).isNull();
    }

    @Test
    void progressIsWeightedAndSkippedPhasesDoNotCount() {
        Project project = projectBetween("2027-01-01", "2027-04-10");
        List<ProjectPhase> phases = ProjectPhasePlanner.build(project, List.of(
                new ProjectPhasePlanner.PhaseSeed("A", 1, new BigDecimal("20")),
                new ProjectPhasePlanner.PhaseSeed("B", 2, new BigDecimal("30")),
                new ProjectPhasePlanner.PhaseSeed("C", 3, new BigDecimal("50"))), "x");
        phases.get(0).setStatus(ProjectPhaseStatus.DONE);
        phases.get(1).setStatus(ProjectPhaseStatus.IN_PROGRESS);
        phases.get(1).setProgress(50);

        // (20×100 + 30×50 + 50×0) / 100 = 35
        assertThat(ProjectPhasePlanner.weightedProgress(phases)).isEqualTo(35);
        assertThat(ProjectPhasePlanner.currentPhaseName(phases)).isEqualTo("B");

        phases.get(2).setStatus(ProjectPhaseStatus.SKIPPED);
        // (20×100 + 30×50) / 50 = 70
        assertThat(ProjectPhasePlanner.weightedProgress(phases)).isEqualTo(70);
    }

    private static Project projectBetween(String start, String end) {
        Project project = new Project();
        ProjectExecution execution = new ProjectExecution();
        execution.setPlannedStartDate(LocalDate.parse(start));
        execution.setPlannedEndDate(LocalDate.parse(end));
        project.attachExecution(execution);
        return project;
    }

    private static PhaseModel model(PhaseDefinition... phases) {
        PhaseModel model = new PhaseModel();
        model.getPhases().addAll(List.of(phases));
        return model;
    }

    private static PhaseDefinition phase(String name, int sequence, String weight) {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal(weight));
        return phase;
    }

    private static DemandCustomPhase custom(String name, int sequence, String weight, boolean active) {
        DemandCustomPhase phase = new DemandCustomPhase();
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal(weight));
        phase.setActive(active);
        return phase;
    }
}
