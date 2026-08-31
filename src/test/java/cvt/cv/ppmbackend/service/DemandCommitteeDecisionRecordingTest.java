package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCommitteeDecisionDtos.DecisionCommand;
import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCommitteeDecision;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.CommitteeDecision;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.DemandCommitteeDecisionRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.OperationalPlanRepository;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import cvt.cv.ppmbackend.repository.StrategicObjectiveRepository;
import cvt.cv.ppmbackend.repository.StrategicPillarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemandCommitteeDecisionRecordingTest {

    @Mock
    private DemandPlanningCycleRepository cycles;
    @Mock
    private DemandCommitteeDecisionRepository decisionRepository;
    @Mock
    private StrategicPlanService strategicPlans;
    @Mock
    private OperationalPlanRepository operationalPlans;
    @Mock
    private StrategicPillarRepository strategicPillars;
    @Mock
    private StrategicElementRepository strategicElements;
    @Mock
    private StrategicObjectiveRepository strategicObjectives;
    @Mock
    private ProgramRepository programs;
    @Mock
    private DemandScoreLifecycleService scoreLifecycle;
    @Mock
    private DemandHistoryService historyService;

    private DemandPlanningCycleService service;
    private StrategicPlan plan;
    private Committee committee;

    @BeforeEach
    void setUp() {
        service = new DemandPlanningCycleService(cycles, decisionRepository, strategicPlans, operationalPlans,
                strategicPillars, strategicElements, strategicObjectives, programs, scoreLifecycle, historyService);
        plan = new StrategicPlan();
        plan.setId(UUID.randomUUID());
        committee = new Committee();
        committee.setId(UUID.randomUUID());
        committee.setName("Comité de TI");
        when(cycles.save(any(DemandPlanningCycle.class))).thenAnswer(i -> i.getArgument(0));
        when(decisionRepository.save(any(DemandCommitteeDecision.class))).thenAnswer(invocation -> {
            DemandCommitteeDecision recorded = invocation.getArgument(0);
            recorded.setId(UUID.randomUUID());
            return recorded;
        });
    }

    @Test
    void approvedDecisionMovesCycleToApprovedAndRecordsTheCommittee() {
        DemandPlanningCycle cycle = cycle(DemandPlanningStatus.AWAITING_COMMITTEE);

        DemandCommitteeDecision recorded = service.applyCommitteeDecision(cycle,
                new DecisionCommand(CommitteeDecision.APPROVED, null, null, "ATA-01", "user-1", "Ana"));

        assertThat(cycle.getStatus()).isEqualTo(DemandPlanningStatus.APPROVED);
        assertThat(recorded.getDecision()).isEqualTo(CommitteeDecision.APPROVED);
        assertThat(recorded.getCommittee()).isSameAs(committee);
        assertThat(recorded.getJustification()).isNull();
        assertThat(recorded.getDecidedBy()).isEqualTo("Ana");
        assertThat(recorded.getMeetingReference()).isEqualTo("ATA-01");
    }

    @Test
    void conditionalApprovalStoresConditionsSeparatelyFromJustification() {
        DemandPlanningCycle cycle = cycle(DemandPlanningStatus.AWAITING_COMMITTEE);

        DemandCommitteeDecision recorded = service.applyCommitteeDecision(cycle,
                new DecisionCommand(CommitteeDecision.CONDITIONALLY_APPROVED, "Depende de orcamento adicional",
                        "Rever ambito ate 30/09", null, "user-1", "Ana"));

        assertThat(cycle.getStatus()).isEqualTo(DemandPlanningStatus.CONDITIONALLY_APPROVED);
        assertThat(recorded.getJustification()).isEqualTo("Depende de orcamento adicional");
        assertThat(recorded.getConditions()).isEqualTo("Rever ambito ate 30/09");
    }

    @Test
    void requestRevisionMovesCycleBackToAnalysis() {
        DemandPlanningCycle cycle = cycle(DemandPlanningStatus.AWAITING_COMMITTEE);

        DemandCommitteeDecision recorded = service.applyCommitteeDecision(cycle,
                new DecisionCommand(CommitteeDecision.REQUEST_REVISION, "Falta detalhe de custos", null, null,
                        "user-1", "Ana"));

        assertThat(cycle.getStatus()).isEqualTo(DemandPlanningStatus.IN_ANALYSIS);
        assertThat(recorded.getDecision()).isEqualTo(CommitteeDecision.REQUEST_REVISION);
    }

    @Test
    void justificationIsMandatoryForConditionalApprovalAndRevision() {
        assertThatThrownBy(() -> service.applyCommitteeDecision(cycle(DemandPlanningStatus.AWAITING_COMMITTEE),
                new DecisionCommand(CommitteeDecision.CONDITIONALLY_APPROVED, "  ", "condicao", null, "user-1", "Ana")))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.applyCommitteeDecision(cycle(DemandPlanningStatus.AWAITING_COMMITTEE),
                new DecisionCommand(CommitteeDecision.REQUEST_REVISION, null, null, null, "user-1", "Ana")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void successiveDecisionsAreAppendedInsteadOfOverwritingThePreviousOne() {
        DemandPlanningCycle cycle = cycle(DemandPlanningStatus.AWAITING_COMMITTEE);

        service.applyCommitteeDecision(cycle, new DecisionCommand(CommitteeDecision.CONDITIONALLY_APPROVED,
                "Depende de orcamento", "Rever ambito", null, "user-1", "Ana"));
        service.applyCommitteeDecision(cycle, new DecisionCommand(CommitteeDecision.APPROVED, null, null, null,
                "user-2", "Bruno"));

        ArgumentCaptor<DemandCommitteeDecision> captor = ArgumentCaptor.forClass(DemandCommitteeDecision.class);
        verify(decisionRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues()).extracting(DemandCommitteeDecision::getDecision)
                .containsExactly(CommitteeDecision.CONDITIONALLY_APPROVED, CommitteeDecision.APPROVED);
        assertThat(cycle.getCommitteeDecisions()).hasSize(2);
        assertThat(cycle.getStatus()).isEqualTo(DemandPlanningStatus.APPROVED);
    }

    @Test
    void legacyDecisionNamesRemainAccepted() {
        assertThat(CommitteeDecision.from("CONDITIONALLY_APPROVE"))
                .isEqualTo(CommitteeDecision.CONDITIONALLY_APPROVED);
        assertThat(CommitteeDecision.from("REVISION_REQUESTED")).isEqualTo(CommitteeDecision.REQUEST_REVISION);
        assertThat(CommitteeDecision.from("backlog")).isEqualTo(CommitteeDecision.BACKLOG);
        assertThat(CommitteeDecision.from("NAO_EXISTE")).isNull();
    }

    private DemandPlanningCycle cycle(DemandPlanningStatus status) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode("DEM-001");
        demand.setTitle("Demanda");

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setResponsibleCommittee(committee);
        cycle.setStatus(status);
        return cycle;
    }
}
