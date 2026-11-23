package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.dto.NotificationDtos;
import cvt.cv.ppmbackend.dto.ProjectCostDtos;
import cvt.cv.ppmbackend.dto.ProjectDependencyDtos;
import cvt.cv.ppmbackend.dto.ProjectPhaseGateDtos;
import cvt.cv.ppmbackend.dto.ProjectUpdateRequest;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProjectPhaseTwoIntegrationTests {
    @Autowired private ObjectMapper mapper;
    @Autowired private ProjectRepository projects;
    @Autowired private ProgramRepository programs;
    @Autowired private LookupValueRepository lookups;
    @Autowired private ProjectMilestoneRepository milestones;
    @Autowired private ProjectPhaseRepository phases;
    @Autowired private ProjectBaselineRepository baselines;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectCostService costService;
    @Autowired private ProjectChangeRequestService changeRequestService;
    @Autowired private ProjectPhaseGateService gateService;
    @Autowired private ProjectDependencyService dependencyService;
    @Autowired private NotificationService notificationService;

    @Test
    void partialProjectUpdateKeepsProgramBudgetAndPhaseAndReturnsIds() throws Exception {
        Program program = new Program();
        program.setName("Transformação");
        program = programs.save(program);
        LookupValue phase = lookup("PROJECT_PHASE", "EXECUTION", "Execução");
        Project project = project("PRJ-PARTIAL", ProjectStatus.PLANNED,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        project.setProgram(program);
        project.setProjectPhase(phase);
        project.setBudget(new BigDecimal("125000.00"));
        projects.saveAndFlush(project);

        ProjectUpdateRequest patch = mapper.readValue("{\"name\":\"Nome atualizado\"}", ProjectUpdateRequest.class);
        var response = projectService.update(project.getId(), patch, "gestor");

        assertThat(response.name()).isEqualTo("Nome atualizado");
        assertThat(response.programId()).isEqualTo(program.getId());
        assertThat(response.budget()).isEqualByComparingTo("125000.00");
        Project reloaded = projects.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getProjectPhase().getId()).isEqualTo(phase.getId());
    }

    @Test
    void startCapturesBaselineAndApprovedScheduleCreatesNextVersion() {
        Project project = project("PRJ-BASELINE", ProjectStatus.PLANNED,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 4, 30));
        cvt.cv.ppmbackend.entity.ProjectPhase phase = phase(project, "Execução", ProjectPhaseStatus.NOT_STARTED);
        ProjectMilestone milestone = milestone(project, phase, "Go-live", LocalDate.of(2027, 4, 20), false);
        projects.saveAndFlush(project);
        phases.saveAndFlush(phase);
        milestones.saveAndFlush(milestone);

        projectService.start(project.getId(), "gestor");

        var snapshots = baselines.findByProjectIdOrderByVersionDesc(project.getId());
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getVersion()).isEqualTo(1);
        assertThat(snapshots.get(0).getPhases()).extracting(ProjectBaselinePhase::getName).containsExactly("Execução");
        assertThat(snapshots.get(0).getMilestones()).extracting(ProjectBaselineMilestone::getName)
                .containsExactly("Go-live");

        var change = changeRequestService.create(project.getId(),
                new cvt.cv.ppmbackend.dto.ProjectManagementDtos.ChangeRequestCreate(ChangeRequestKind.SCHEDULE,
                        "Novo prazo", "Dependência externa", null, LocalDate.of(2027, 6, 30), null, null),
                "gestor");
        changeRequestService.approve(project.getId(), change.id(),
                new cvt.cv.ppmbackend.dto.ProjectManagementDtos.DecisionRequest("Aprovado"), "pmo");
        snapshots = baselines.findByProjectIdOrderByVersionDesc(project.getId());
        assertThat(snapshots).extracting(ProjectBaseline::getVersion).containsExactly(2, 1);
        assertThat(snapshots.get(0).getReason()).isEqualTo("Novo prazo");
        assertThat(snapshots.get(0).getPlannedEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
    }

    @Test
    void costsRecalculateConsumedBudgetAndErpRowsAreImmutable() {
        Project project = projects.saveAndFlush(project("PRJ-COST", ProjectStatus.PLANNED,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30)));
        LookupValue line = lookup("BUDGET_LINE", "SERVICES", "Serviços");

        costService.create(project.getId(), new ProjectCostDtos.Request(line.getId(), "2027-02",
                ProjectCostType.OPEX, new BigDecimal("1000"), new BigDecimal("400"), null,
                ProjectCostSource.MANUAL), "gestor");
        var erp = costService.create(project.getId(), new ProjectCostDtos.Request(line.getId(), "2027-03",
                ProjectCostType.CAPEX, new BigDecimal("2000"), new BigDecimal("600"), null,
                ProjectCostSource.ERP), "erp");

        assertThat(projects.findById(project.getId()).orElseThrow().getExecution().getConsumedBudget())
                .isEqualByComparingTo("1000.00");
        assertThatThrownBy(() -> costService.update(project.getId(), erp.id(),
                new ProjectCostDtos.Request(line.getId(), "2027-03", ProjectCostType.CAPEX,
                        new BigDecimal("2100"), new BigDecimal("600"), null, ProjectCostSource.ERP), "gestor"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void gateNeedsJustificationThenPmoCanApprove() {
        Project project = projects.saveAndFlush(project("PRJ-GATE", ProjectStatus.IN_PROGRESS,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30)));
        cvt.cv.ppmbackend.entity.ProjectPhase phase = phases.saveAndFlush(
                phase(project, "Execução", ProjectPhaseStatus.IN_PROGRESS));
        milestones.saveAndFlush(milestone(project, phase, "Entregável", LocalDate.now(), true));

        assertThatThrownBy(() -> gateService.pass(project.getId(), phase.getId(),
                new ProjectPhaseGateDtos.PassRequest(null), "gestor")).isInstanceOf(BadRequestException.class);

        var requested = gateService.pass(project.getId(), phase.getId(),
                new ProjectPhaseGateDtos.PassRequest("Aceitar exceção"), "gestor");
        assertThat(requested.status()).isEqualTo(ProjectPhaseGateStatus.REQUESTED);
        assertThat(phases.findById(phase.getId()).orElseThrow().getStatus()).isEqualTo(ProjectPhaseStatus.IN_PROGRESS);

        gateService.approve(project.getId(), phase.getId(), new ProjectPhaseGateDtos.DecisionRequest("Aprovado"), "pmo");
        assertThat(phases.findById(phase.getId()).orElseThrow().getStatus()).isEqualTo(ProjectPhaseStatus.DONE);
    }

    @Test
    void dependencyReportsConflictAndRejectsIndirectCycle() {
        Project a = projects.saveAndFlush(project("PRJ-A", ProjectStatus.PLANNED,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)));
        Project b = projects.saveAndFlush(project("PRJ-B", ProjectStatus.PLANNED,
                LocalDate.of(2027, 1, 15), LocalDate.of(2027, 3, 31)));

        var dependency = dependencyService.create(new ProjectDependencyDtos.Request(a.getId(), b.getId(),
                ProjectDependencyType.FINISH_TO_START, null, null), "gestor");
        assertThat(dependency.conflictDays()).isPositive();
        assertThatThrownBy(() -> dependencyService.create(new ProjectDependencyDtos.Request(b.getId(), a.getId(),
                ProjectDependencyType.FINISH_TO_START, null, null), "gestor"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void lateMilestoneNotificationKeepsReadState() {
        Project project = projects.saveAndFlush(project("PRJ-ALERT", ProjectStatus.IN_PROGRESS,
                LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(2)));
        project.setProjectManagerUsername("system");
        projects.saveAndFlush(project);
        milestones.saveAndFlush(milestone(project, null, "Go-live", LocalDate.now().minusDays(12), false));

        var alert = notificationService.list().stream()
                .filter(n -> n.kind().equals("MILESTONE_LATE")).findFirst().orElseThrow();
        assertThat(alert.read()).isFalse();
        notificationService.markRead(new NotificationDtos.ReadRequest(java.util.List.of(alert.id())));
        assertThat(notificationService.list()).filteredOn(n -> n.id().equals(alert.id()))
                .extracting(NotificationDtos.Response::read).containsExactly(true);
    }

    private Project project(String code, ProjectStatus status, LocalDate start, LocalDate end) {
        Project project = new Project();
        project.setCode(code + "-" + java.util.UUID.randomUUID().toString().substring(0, 6));
        project.setName(code);
        project.setOrigin(ProjectOrigin.EXTRA_PLAN);
        project.setDirectionName("Direção de Sistemas");
        project.setExtraPlanJustification("Teste");
        project.setStatus(status);
        project.setBudget(new BigDecimal("10000"));
        ProjectExecution execution = new ProjectExecution();
        execution.setPlannedStartDate(start);
        execution.setPlannedEndDate(end);
        project.attachExecution(execution);
        return project;
    }

    private cvt.cv.ppmbackend.entity.ProjectPhase phase(Project project, String name, ProjectPhaseStatus status) {
        cvt.cv.ppmbackend.entity.ProjectPhase phase = new cvt.cv.ppmbackend.entity.ProjectPhase();
        phase.setProject(project);
        phase.setName(name);
        phase.setSequence(1);
        phase.setWeight(new BigDecimal("100"));
        phase.setPlannedStartDate(project.getExecution().getPlannedStartDate());
        phase.setPlannedEndDate(project.getExecution().getPlannedEndDate());
        phase.setStatus(status);
        if (status == ProjectPhaseStatus.IN_PROGRESS) phase.setActualStartDate(LocalDate.now());
        return phase;
    }

    private ProjectMilestone milestone(Project project, cvt.cv.ppmbackend.entity.ProjectPhase phase, String name, LocalDate due,
            boolean deliverable) {
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProject(project);
        milestone.setPhase(phase);
        milestone.setName(name);
        milestone.setDueDate(due);
        milestone.setStatus(MilestoneStatus.PENDING);
        milestone.setDeliverable(deliverable);
        return milestone;
    }

    private LookupValue lookup(String category, String code, String label) {
        LookupValue lookup = new LookupValue();
        lookup.setCategory(category);
        lookup.setCode(code + "_" + java.util.UUID.randomUUID().toString().substring(0, 6));
        lookup.setLabel(label);
        lookup.setActive(true);
        return lookups.saveAndFlush(lookup);
    }
}
