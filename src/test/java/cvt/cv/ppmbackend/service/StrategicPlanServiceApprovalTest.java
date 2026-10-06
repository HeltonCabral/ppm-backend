package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.ConditionalApprovalResponse;
import cvt.cv.ppmbackend.dto.StrategicPlanApprovalDtos.FinalApprovalResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategicPlanServiceApprovalTest {

    @Mock
    private StrategicPlanRepository plans;
    @Mock
    private DemandPlanningCycleRepository planningCycles;
    @Mock
    private StrategicObjectiveRepository objectives;
    @Mock
    private ProgramRepository programs;
    @Mock
    private ProjectService projects;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private OperationalPlanRepository operationalPlans;
    @Mock
    private DemandHistoryService historyService;
    @Mock
    private DemandScoreLifecycleService scoreLifecycle;
    @Mock
    private StrategicPlanningConfigService planningConfig;

    private StrategicPlanService service;

    @BeforeEach
    void setUp() {
        service = new StrategicPlanService(plans, planningCycles, objectives, programs, projects,
                projectRepository, operationalPlans, historyService, scoreLifecycle, planningConfig);
    }

    @Test
    void directlyApprovesDraftPlanCreatesBacklogProjectAndActivatesPlan() {
        UUID planId = UUID.randomUUID();
        String actorId = "user-1";
        String actorName = "Utilizador Teste";
        StrategicPlan plan = plan(planId, StrategicPlanStatus.DRAFT);
        StrategicPlan previousActivePlan = plan(UUID.randomUUID(), StrategicPlanStatus.ACTIVE);

        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode("DEM-001");
        demand.setTitle("Demanda para backlog");
        demand.setDirectionName("Direção de Sistemas");
        demand.setDirectionCode("DSI");
        demand.setDesiredOutcome("Resultado esperado");

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setStatus(DemandPlanningStatus.PRIORITIZED);

        List<DemandPlanningStatus> approvedStatuses = List.of(DemandPlanningStatus.IN_PLAN);
        List<DemandPlanningStatus> backlogStatuses = List.of(
                DemandPlanningStatus.DRAFT, DemandPlanningStatus.IN_ANALYSIS,
                DemandPlanningStatus.IN_REVIEW, DemandPlanningStatus.IN_PRIORITIZATION,
                DemandPlanningStatus.PRIORITIZED, DemandPlanningStatus.BACKLOG);

        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        when(planningCycles.findAllByStrategicPlan_Id(planId)).thenReturn(List.of(cycle));
        when(planningCycles.findAllByStrategicPlan_IdAndStatusIn(planId, approvedStatuses)).thenReturn(List.of());
        when(planningCycles.findAllByStrategicPlan_IdAndStatusIn(planId, backlogStatuses))
                .thenReturn(List.of(cycle));
        when(plans.findFirstByStatus(StrategicPlanStatus.ACTIVE)).thenReturn(Optional.of(previousActivePlan));
        when(plans.save(any(StrategicPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planningCycles.save(cycle)).thenReturn(cycle);
        when(projects.saveFromDemand(any(Project.class), eq(demand), eq(cycle),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(actorId)))
                .thenAnswer(invocation -> {
                    Project project = invocation.getArgument(0);
                    project.setId(UUID.randomUUID());
                    return project;
                });

        FinalApprovalResponse response = service.approve(planId, actorId, actorName);

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projects).saveFromDemand(projectCaptor.capture(), eq(demand), eq(cycle),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(actorId));
        Project backlogProject = projectCaptor.getValue();

        assertThat(response.status()).isEqualTo(StrategicPlanStatus.ACTIVE);
        assertThat(response.movedToBacklog()).isEqualTo(1);
        assertThat(response.backlogProjectsCreated()).isEqualTo(1);
        assertThat(plan.getStatus()).isEqualTo(StrategicPlanStatus.ACTIVE);
        assertThat(plan.getApprovalDate()).isEqualTo(LocalDate.now());
        assertThat(plan.getApprovedBy()).isEqualTo(actorName);
        assertThat(previousActivePlan.getStatus()).isEqualTo(StrategicPlanStatus.SUPERSEDED);
        assertThat(backlogProject.getStatus()).isEqualTo(ProjectStatus.BACKLOG);
        assertThat(backlogProject.getCreatedFromDemandBacklog()).isTrue();
        assertThat(backlogProject.getCreatedFromConditionalPlanApproval()).isFalse();
        assertThat(cycle.getStatus()).isEqualTo(DemandPlanningStatus.BACKLOG);
        assertThat(cycle.getConvertedProject()).isSameAs(backlogProject);
        assertThat(cycle.getConvertedBy()).isEqualTo(actorId);
        assertThat(cycle.getConvertedAt()).isNotNull();
        verify(scoreLifecycle).applyStatusTransition(cycle, DemandPlanningStatus.PRIORITIZED,
                DemandPlanningStatus.BACKLOG,
                "Demanda não selecionada pelo cenário final do Plano Estratégico");
    }

    @Test
    void conditionalApprovalStillLeavesPlanConditionallyApproved() {
        UUID planId = UUID.randomUUID();
        StrategicPlan plan = plan(planId, StrategicPlanStatus.DRAFT);

        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        when(planningCycles.findAllByStrategicPlan_IdAndStatusIn(planId, List.of(DemandPlanningStatus.IN_PLAN)))
                .thenReturn(List.of());
        when(plans.save(plan)).thenReturn(plan);

        ConditionalApprovalResponse response = service.conditionalApprove(planId, "user-1", "Utilizador Teste");

        assertThat(response.status()).isEqualTo(StrategicPlanStatus.CONDITIONALLY_APPROVED);
        assertThat(plan.getStatus()).isEqualTo(StrategicPlanStatus.CONDITIONALLY_APPROVED);
    }

    @Test
    void demandsAlreadyInBacklogAreConvertedToBacklogProjectsOnFinalApproval() {
        UUID planId = UUID.randomUUID();
        String actorId = "user-1";
        StrategicPlan plan = plan(planId, StrategicPlanStatus.CONDITIONALLY_APPROVED);

        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode("DEM-002");
        demand.setTitle("Demanda em backlog do comité");
        demand.setDirectionName("Direção de Sistemas");
        demand.setDirectionCode("DSI");

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setStatus(DemandPlanningStatus.BACKLOG);

        List<DemandPlanningStatus> backlogStatuses = List.of(
                DemandPlanningStatus.DRAFT, DemandPlanningStatus.IN_ANALYSIS,
                DemandPlanningStatus.IN_REVIEW, DemandPlanningStatus.IN_PRIORITIZATION,
                DemandPlanningStatus.PRIORITIZED, DemandPlanningStatus.BACKLOG);

        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        when(planningCycles.findAllByStrategicPlan_Id(planId)).thenReturn(List.of(cycle));
        when(planningCycles.findAllByStrategicPlan_IdAndStatusIn(planId, backlogStatuses)).thenReturn(List.of(cycle));
        when(plans.findFirstByStatus(StrategicPlanStatus.ACTIVE)).thenReturn(Optional.empty());
        when(plans.save(any(StrategicPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planningCycles.save(cycle)).thenReturn(cycle);
        when(projects.saveFromDemand(any(Project.class), eq(demand), eq(cycle),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(actorId)))
                .thenAnswer(invocation -> {
                    Project project = invocation.getArgument(0);
                    project.setId(UUID.randomUUID());
                    return project;
                });

        FinalApprovalResponse response = service.approve(planId, actorId, "Utilizador Teste");

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projects).saveFromDemand(projectCaptor.capture(), eq(demand), eq(cycle),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(actorId));

        assertThat(projectCaptor.getValue().getStatus()).isEqualTo(ProjectStatus.BACKLOG);
        assertThat(projectCaptor.getValue().getCreatedFromDemandBacklog()).isTrue();
        assertThat(cycle.getStatus()).isEqualTo(DemandPlanningStatus.BACKLOG);
        assertThat(cycle.getConvertedProject()).isSameAs(projectCaptor.getValue());
        assertThat(response.backlogProjectsCreated()).isEqualTo(1);
        assertThat(response.movedToBacklog()).isZero();
    }

    private StrategicPlan plan(UUID id, StrategicPlanStatus status) {
        StrategicPlan plan = new StrategicPlan();
        plan.setId(id);
        plan.setName("Plano Estratégico");
        plan.setStartYear(2027);
        plan.setEndYear(2029);
        plan.setStatus(status);
        return plan;
    }
}
