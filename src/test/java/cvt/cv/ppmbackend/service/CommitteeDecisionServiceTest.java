package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.CapacityThresholdsProperties;
import cvt.cv.ppmbackend.dto.CommitteeDecisionDtos.DecisionDashboardResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
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

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private StrategicPlanRepository strategicPlans;
    @Mock
    private DemandProfileRequirementRepository profileRequirements;
    @Mock
    private DemandResourceDerivationService resourceDerivation;
    @Mock
    private DemandDependencyRepository dependencies;

    private CommitteeDecisionService service;
    private UUID planId;

    @BeforeEach
    void setUp() {
        service = new CommitteeDecisionService(planningCycles, strategicPlans,
                new DemandRequirementsResolver(resourceDerivation, profileRequirements), dependencies,
                new CapacityCalculator(new CapacityThresholdsProperties()));
        planId = UUID.randomUUID();
        StrategicPlan plan = new StrategicPlan();
        plan.setId(planId);
        plan.setName("Plano Estratégico 2026-2028");
        plan.setStartYear(2026);
        plan.setEndYear(2028);
        plan.setStatus(StrategicPlanStatus.CONDITIONALLY_APPROVED);
        when(strategicPlans.findById(planId)).thenReturn(Optional.of(plan));
    }

    @Test
    void buildsDecisionDashboardFromPlanScenarioCapacityAndDependencies() {
        // As duas Demandas do plano correm em paralelo: 2 em simultâneo para capacidade 1 → fora dos limites.
        DemandPlanningCycle approved = cycle(DemandPlanningStatus.IN_PLAN, "100.00", "2027-01-01", "2027-06-30");
        DemandPlanningCycle conditional = cycle(DemandPlanningStatus.IN_PLAN, "50.00", "2027-01-01", "2027-06-30");
        DemandPlanningCycle pending = cycle(DemandPlanningStatus.PRIORITIZED, "25.00", null, null);
        DemandPlanningCycle revision = cycle(DemandPlanningStatus.IN_REVIEW, "10.00", null, null);
        Profile profile = profile(1, 1);

        DemandDependency dependency = new DemandDependency();
        dependency.setId(UUID.randomUUID());
        dependency.setDemand(approved.getDemand());
        dependency.setDependsOnDemand(pending.getDemand());
        dependency.setType(DemandDependencyType.BLOCKING);

        when(planningCycles.findAllByStrategicPlan_Id(planId))
                .thenReturn(List.of(approved, conditional, pending, revision));
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(approved.getDemand().getId()))
                .thenReturn(List.of(requirement(approved.getDemand(), profile, 2)));
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(conditional.getDemand().getId()))
                .thenReturn(List.of(requirement(conditional.getDemand(), profile, 1)));
        when(dependencies.findByDemand_IdIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(dependency));

        DecisionDashboardResponse result = service.dashboard(planId);

        assertThat(result.plan().deliberatedDemands()).isEqualTo(2);
        assertThat(result.plan().totalDemands()).isEqualTo(4);
        assertThat(result.plan().deliberationPercentage()).isEqualTo(50);
        assertThat(result.scenario().demandsCount()).isEqualTo(2);
        assertThat(result.scenario().requiredBudget()).isEqualByComparingTo("150.00");
        assertThat(result.scenario().pendingDecisions()).isEqualTo(1);
        assertThat(result.scenario().openConditions()).isEqualTo(1);
        assertThat(result.overview().totalBudget()).isEqualByComparingTo("25.00");
        assertThat(result.overview().capacity().required()).isEqualByComparingTo("3.00");
        assertThat(result.overview().capacity().available()).isEqualByComparingTo("1.00");
        assertThat(result.overview().capacity().coveragePercentage()).isEqualByComparingTo("33.33");
        assertThat(result.overview().deficitProfiles()).isEqualTo(1);
        assertThat(result.overview().blockedDemands()).isEqualTo(1);
        assertThat(result.overview().criticalDependencies()).isEqualTo(1);
        assertThat(result.attentionPoints()).extracting("code")
                .containsExactly("CAPACITY_DEFICIT", "BLOCKING_DEPENDENCIES", "OPEN_CONDITIONS");
    }

    @Test
    void demandsThatDoNotOverlapInTimeAreNotADeficit() {
        // Mesmo perfil, mas uma Demanda no 1.º semestre e outra no 2.º: nunca há 2 em simultâneo.
        DemandPlanningCycle first = cycle(DemandPlanningStatus.IN_PLAN, "10.00", "2027-01-01", "2027-06-30");
        DemandPlanningCycle second = cycle(DemandPlanningStatus.IN_PLAN, "10.00", "2027-07-01", "2027-12-31");
        Profile profile = profile(2, 2);

        when(planningCycles.findAllByStrategicPlan_Id(planId)).thenReturn(List.of(first, second));
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(first.getDemand().getId()))
                .thenReturn(List.of(requirement(first.getDemand(), profile, 2)));
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(second.getDemand().getId()))
                .thenReturn(List.of(requirement(second.getDemand(), profile, 2)));

        DecisionDashboardResponse result = service.dashboard(planId);

        // Necessário = carga no mês de pico (2), não a soma das Demandas (4).
        assertThat(result.overview().capacity().required()).isEqualByComparingTo("2.00");
        assertThat(result.overview().capacity().coveragePercentage()).isEqualByComparingTo("100.00");
        assertThat(result.overview().deficitProfiles()).isZero();
        assertThat(result.attentionPoints()).extracting("code").doesNotContain("CAPACITY_DEFICIT");
    }

    private DemandPlanningCycle cycle(DemandPlanningStatus status, String budget, String start, String end) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setEstimatedBudget(new BigDecimal(budget));
        demand.setPlannedStartDate(start == null ? null : LocalDate.parse(start));
        demand.setDesiredDate(end == null ? null : LocalDate.parse(end));

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStatus(status);
        return cycle;
    }

    private static Profile profile(int available, int simultaneous) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName("Backend");
        profile.setAvailableCapacity(available);
        profile.setSimultaneousDemandCapacity(simultaneous);
        return profile;
    }

    private DemandProfileRequirement requirement(Demand demand, Profile profile, int quantity) {
        DemandProfileRequirement requirement = new DemandProfileRequirement();
        requirement.setId(UUID.randomUUID());
        requirement.setDemand(demand);
        requirement.setProfile(profile);
        requirement.setRequiredQuantity(quantity);
        return requirement;
    }
}
