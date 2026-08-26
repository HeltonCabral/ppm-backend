package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CommitteeDecisionDtos.DecisionDashboardResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitteeDecisionServiceTest {

    @Mock
    private DemandPlanningCycleRepository planningCycles;
    @Mock
    private DemandPlanningCycleService planningCycleService;
    @Mock
    private StrategicPlanRepository strategicPlans;
    @Mock
    private DemandProfileRequirementRepository profileRequirements;
    @Mock
    private DemandDependencyRepository dependencies;

    private CommitteeDecisionService service;

    @BeforeEach
    void setUp() {
        service = new CommitteeDecisionService(
                planningCycles, planningCycleService, strategicPlans, profileRequirements, dependencies);
    }

    @Test
    void buildsDecisionDashboardFromPlanScenarioCapacityAndDependencies() {
        UUID planId = UUID.randomUUID();
        StrategicPlan plan = new StrategicPlan();
        plan.setId(planId);
        plan.setName("Plano Estratégico 2026-2028");
        plan.setStartYear(2026);
        plan.setEndYear(2028);
        plan.setStatus(StrategicPlanStatus.CONDITIONALLY_APPROVED);

        DemandPlanningCycle approved = cycle(DemandPlanningStatus.APPROVED, "APPROVED", "100.00");
        DemandPlanningCycle conditional = cycle(DemandPlanningStatus.CONDITIONALLY_APPROVED, "CONDITIONALLY_APPROVED", "50.00");
        DemandPlanningCycle pending = cycle(DemandPlanningStatus.IN_PRIORITIZATION, null, "25.00");
        DemandPlanningCycle revision = cycle(DemandPlanningStatus.IN_ANALYSIS, "REVISION_REQUESTED", "10.00");

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName("Backend");
        profile.setAvailableCapacity(1);
        DemandProfileRequirement firstRequirement = requirement(approved.getDemand(), profile, 2, 100);
        DemandProfileRequirement secondRequirement = requirement(conditional.getDemand(), profile, 1, 50);

        DemandDependency dependency = new DemandDependency();
        dependency.setId(UUID.randomUUID());
        dependency.setDemand(approved.getDemand());
        dependency.setDependsOnDemand(pending.getDemand());
        dependency.setType(DemandDependencyType.BLOCKING);

        when(strategicPlans.findById(planId)).thenReturn(Optional.of(plan));
        when(planningCycles.findAllByStrategicPlan_Id(planId))
                .thenReturn(List.of(approved, conditional, pending, revision));
        when(profileRequirements.findByDemand_IdIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(firstRequirement, secondRequirement));
        when(dependencies.findByDemand_IdIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(dependency));

        DecisionDashboardResponse result = service.dashboard(planId);

        assertThat(result.plan().deliberatedDemands()).isEqualTo(3);
        assertThat(result.plan().totalDemands()).isEqualTo(4);
        assertThat(result.plan().deliberationPercentage()).isEqualTo(75);
        assertThat(result.scenario().demandsCount()).isEqualTo(2);
        assertThat(result.scenario().requiredBudget()).isEqualByComparingTo("150.00");
        assertThat(result.scenario().pendingDecisions()).isEqualTo(1);
        assertThat(result.scenario().openConditions()).isEqualTo(1);
        assertThat(result.overview().totalBudget()).isEqualByComparingTo("25.00");
        assertThat(result.overview().capacity().required()).isEqualByComparingTo("2.50");
        assertThat(result.overview().capacity().available()).isEqualByComparingTo("1.00");
        assertThat(result.overview().capacity().coveragePercentage()).isEqualByComparingTo("40.00");
        assertThat(result.overview().deficitProfiles()).isEqualTo(1);
        assertThat(result.overview().blockedDemands()).isEqualTo(1);
        assertThat(result.overview().criticalDependencies()).isEqualTo(1);
        assertThat(result.attentionPoints()).extracting("code")
                .containsExactly("CAPACITY_DEFICIT", "BLOCKING_DEPENDENCIES", "OPEN_CONDITIONS");
    }

    private DemandPlanningCycle cycle(DemandPlanningStatus status, String committeeDecision, String budget) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setEstimatedBudget(new BigDecimal(budget));

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStatus(status);
        cycle.setCommitteeDecision(committeeDecision);
        return cycle;
    }

    private DemandProfileRequirement requirement(
            Demand demand, Profile profile, int quantity, int percentage) {
        DemandProfileRequirement requirement = new DemandProfileRequirement();
        requirement.setId(UUID.randomUUID());
        requirement.setDemand(demand);
        requirement.setProfile(profile);
        requirement.setRequiredQuantity(quantity);
        requirement.setAllocationPercentage(percentage);
        return requirement;
    }
}
