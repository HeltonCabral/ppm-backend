package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.ProjectOrigin;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.ProjectExecutionRankHistoryRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import cvt.cv.ppmbackend.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceSaveFromDemandTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectExecutionRankHistoryRepository rankHistory;
    @Mock
    private LookupValueRepository lookups;
    @Mock
    private SupplierRepository suppliers;
    @Mock
    private ProgramRepository programs;
    @Mock
    private StrategicPlanRepository strategicPlans;
    @Mock
    private OperationalPlanRepository operationalPlans;
    @Mock
    private StrategicPillarRepository strategicPillars;
    @Mock
    private StrategicObjectiveRepository strategicObjectives;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projects, rankHistory, lookups, suppliers, programs, strategicPlans,
                operationalPlans, strategicPillars, strategicObjectives);
        when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void keepsBacklogStatusOfProjectsGeneratedFromBacklogDemands() {
        Project project = new Project();
        project.setName("Demanda em backlog");
        project.setDirectionName("Direção de Sistemas");
        project.setStatus(ProjectStatus.BACKLOG);

        Project saved = service.saveFromDemand(project, demand(), cycle(), null, null, null, null, null, "user-1");

        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.BACKLOG);
        assertThat(saved.getOrigin()).isEqualTo(ProjectOrigin.DEMAND);
    }

    @Test
    void forcesBacklogStatusWhenProjectOriginatesFromDemandBacklog() {
        Project project = new Project();
        project.setName("Demanda em backlog");
        project.setDirectionName("Direção de Sistemas");
        project.setCreatedFromDemandBacklog(true);

        Project saved = service.saveFromDemand(project, demand(), cycle(), null, null, null, null, null, "user-1");

        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.BACKLOG);
    }

    @Test
    void defaultsToPlannedWhenNoStatusIsProvided() {
        Project project = new Project();
        project.setName("Demanda aprovada");
        project.setDirectionName("Direção de Sistemas");

        Project saved = service.saveFromDemand(project, demand(), cycle(), null, null, null, null, null, "user-1");

        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.PLANNED);
    }

    private Demand demand() {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode("DEM-001");
        demand.setTitle("Demanda");
        demand.setDirectionName("Direção de Sistemas");
        return demand;
    }

    private DemandPlanningCycle cycle() {
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        return cycle;
    }
}
