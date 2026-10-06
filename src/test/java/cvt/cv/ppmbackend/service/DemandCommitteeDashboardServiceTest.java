package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.CommitteeDashboardResponse;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.PortfolioAttentionPoint;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandCommitteeDashboardServiceTest {

    @Mock
    private StrategicPlanRepository strategicPlans;
    @Mock
    private DemandPlanningCycleRepository planningCycles;
    @Mock
    private DemandProfileRequirementRepository profileRequirements;
    @Mock
    private DemandDependencyRepository dependencies;

    private DemandCommitteeDashboardService service;

    @BeforeEach
    void setUp() {
        service = new DemandCommitteeDashboardService(
                strategicPlans, planningCycles, profileRequirements, dependencies);
    }

    @Test
    void attentionPointsOnlyConsiderPrioritizedDemands() {
        UUID planId = UUID.randomUUID();
        StrategicPlan plan = new StrategicPlan();
        plan.setId(planId);
        plan.setName("Plano Estratégico 2027-2029");
        plan.setStartYear(2027);
        plan.setEndYear(2029);
        plan.setStatus(StrategicPlanStatus.DRAFT);

        DemandPlanningCycle approved = cycle(DemandPlanningStatus.IN_PLAN, DemandNature.REGULATORY);
        DemandPlanningCycle awaitingCommittee = cycle(DemandPlanningStatus.PRIORITIZED, DemandNature.LEGAL);
        DemandPlanningCycle draft = cycle(DemandPlanningStatus.DRAFT, DemandNature.CONTRACTUAL);

        when(strategicPlans.findById(planId)).thenReturn(Optional.of(plan));
        when(planningCycles.findAllByStrategicPlan_Id(planId))
                .thenReturn(List.of(approved, awaitingCommittee, draft));
        when(profileRequirements.findByDemand_IdIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());
        when(dependencies.findByDemand_IdIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());

        CommitteeDashboardResponse response = service.dashboard(planId);

        PortfolioAttentionPoint mandatory = attentionPoint(response, "MANDATORY_DEMANDS");
        PortfolioAttentionPoint criticalDeadlines = attentionPoint(response, "CRITICAL_DEADLINES");

        assertThat(mandatory.count()).isEqualTo(1);
        assertThat(mandatory.detail()).isEqualTo("1 legais");
        assertThat(criticalDeadlines.count()).isEqualTo(1);
        verify(profileRequirements).findByDemand_IdIn(Set.of(awaitingCommittee.getDemand().getId()));
        verify(dependencies).findByDemand_IdIn(Set.of(awaitingCommittee.getDemand().getId()));
    }

    private DemandPlanningCycle cycle(DemandPlanningStatus status, DemandNature nature) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setNature(nature);
        demand.setDesiredDate(LocalDate.now().plusDays(10));

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStatus(status);
        return cycle;
    }

    private PortfolioAttentionPoint attentionPoint(CommitteeDashboardResponse response, String code) {
        return response.attentionPoints().stream()
                .filter(point -> point.code().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
