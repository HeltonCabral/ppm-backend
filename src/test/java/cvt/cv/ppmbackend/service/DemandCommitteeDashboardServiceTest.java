package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.CapacityThresholdsProperties;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.CommitteeDashboardResponse;
import cvt.cv.ppmbackend.dto.DemandCommitteeDashboardDtos.PortfolioAttentionPoint;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.Profile;
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
import static org.mockito.Mockito.never;
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
    private DemandResourceDerivationService resourceDerivation;
    @Mock
    private DemandDependencyRepository dependencies;

    private DemandCommitteeDashboardService service;

    @BeforeEach
    void setUp() {
        service = new DemandCommitteeDashboardService(strategicPlans, planningCycles,
                new DemandRequirementsResolver(resourceDerivation, profileRequirements), dependencies,
                new CapacityCalculator(new CapacityThresholdsProperties()));
    }

    @Test
    void restrictedCapacityUsesTheMonthlyCalculation() {
        UUID planId = planWithCycles(List.of(
                // Analista: as duas primeiras correm em paralelo (2 em simultâneo, capacidade 1) → crítico.
                prioritized("2027-01-01", "2027-06-30"),
                prioritized("2027-01-01", "2027-06-30"),
                // Arquiteto (capacidade 2): uma no 1.º semestre, outra no 2.º → 1 de 2 em simultâneo → OK.
                prioritized("2027-01-01", "2027-06-30"),
                prioritized("2027-07-01", "2027-12-31")));
        List<DemandPlanningCycle> cycles = planningCycles.findAllByStrategicPlan_Id(planId);
        Profile analyst = profile("Analista", 1);
        Profile architect = profile("Arquiteto", 2);
        requires(cycles.get(0), analyst);
        requires(cycles.get(1), analyst);
        requires(cycles.get(2), architect);
        requires(cycles.get(3), architect);

        PortfolioAttentionPoint restricted = attentionPoint(service.dashboard(planId), "CAPACITY_RESTRICTED");

        assertThat(restricted.severity()).isEqualTo("DANGER");
        assertThat(restricted.count()).isEqualTo(2);
        assertThat(restricted.detail()).isEqualTo("Principais perfis fora dos limites: Analista");
    }

    @Test
    void restrictedCapacityIsOkWhenNoProfileIsOutOfBounds() {
        UUID planId = planWithCycles(List.of(
                prioritized("2027-01-01", "2027-06-30"), prioritized("2027-07-01", "2027-12-31")));
        List<DemandPlanningCycle> cycles = planningCycles.findAllByStrategicPlan_Id(planId);
        Profile architect = profile("Arquiteto", 2);
        requires(cycles.get(0), architect);
        requires(cycles.get(1), architect);

        PortfolioAttentionPoint restricted = attentionPoint(service.dashboard(planId), "CAPACITY_RESTRICTED");

        assertThat(restricted.severity()).isEqualTo("OK");
        assertThat(restricted.count()).isZero();
    }

    private UUID planWithCycles(List<DemandPlanningCycle> cycles) {
        UUID planId = UUID.randomUUID();
        StrategicPlan plan = new StrategicPlan();
        plan.setId(planId);
        plan.setName("Plano Estratégico 2027-2029");
        plan.setStartYear(2027);
        plan.setEndYear(2029);
        plan.setStatus(StrategicPlanStatus.DRAFT);
        when(strategicPlans.findById(planId)).thenReturn(Optional.of(plan));
        when(planningCycles.findAllByStrategicPlan_Id(planId)).thenReturn(cycles);
        when(dependencies.findByDemand_IdIn(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of());
        return planId;
    }

    private DemandPlanningCycle prioritized(String start, String end) {
        DemandPlanningCycle cycle = cycle(DemandPlanningStatus.PRIORITIZED, DemandNature.NORMAL);
        cycle.getDemand().setPlannedStartDate(LocalDate.parse(start));
        cycle.getDemand().setDesiredDate(LocalDate.parse(end));
        return cycle;
    }

    private void requires(DemandPlanningCycle cycle, Profile profile) {
        DemandProfileRequirement requirement = new DemandProfileRequirement();
        requirement.setDemand(cycle.getDemand());
        requirement.setProfile(profile);
        requirement.setRequiredQuantity(1);
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(cycle.getDemand().getId()))
                .thenReturn(List.of(requirement));
    }

    private static Profile profile(String name, int simultaneous) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);
        profile.setAvailableCapacity(2);
        profile.setSimultaneousDemandCapacity(simultaneous);
        return profile;
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
        when(dependencies.findByDemand_IdIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());

        CommitteeDashboardResponse response = service.dashboard(planId);

        PortfolioAttentionPoint mandatory = attentionPoint(response, "MANDATORY_DEMANDS");
        PortfolioAttentionPoint criticalDeadlines = attentionPoint(response, "CRITICAL_DEADLINES");

        assertThat(mandatory.count()).isEqualTo(1);
        assertThat(mandatory.detail()).isEqualTo("1 legais");
        assertThat(criticalDeadlines.count()).isEqualTo(1);
        // Atenção: só a PRIORITIZED; o resumo do cenário lê a IN_PLAN. A DRAFT nunca é consultada.
        verify(profileRequirements).findByDemand_IdOrderByProfile_NameAsc(awaitingCommittee.getDemand().getId());
        verify(profileRequirements).findByDemand_IdOrderByProfile_NameAsc(approved.getDemand().getId());
        verify(profileRequirements, never()).findByDemand_IdOrderByProfile_NameAsc(draft.getDemand().getId());
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
