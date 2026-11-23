package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.CapacityThresholdsProperties;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CapacityAnalysis;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ProfileCapacityInfo;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.PhaseProfile;
import cvt.cv.ppmbackend.entity.PlanningScenario;
import cvt.cv.ppmbackend.entity.PlanningScenarioItem;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.PlanningScenarioItemRepository;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Capacidade das análises de plano e de cenário com Demandas cujos perfis vêm das fases do Tipo
 * (sem requisitos gravados): têm de aparecer, como na análise de capacidade da própria Demanda.
 * Usa o {@link CapacityCalculator} e a derivação reais.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningScenarioCapacityTest {

    @Mock private PlanningScenarioService scenarioService;
    @Mock private PlanningScenarioItemRepository items;
    @Mock private ScenarioPoolService pool;
    @Mock private DemandProfileRequirementRepository profileRequirements;
    @Mock private DemandParticipatingDirectionRepository participatingDirections;
    @Mock private DemandDependencyRepository dependencies;
    @Mock private DemandCapacityAnalysisService capacityAnalysis;
    @Mock private StrategicPlanRepository strategicPlans;

    private PlanningScenarioAnalysisService service;
    private StrategicPlan plan;
    private PlanningScenario scenario;
    private List<DemandPlanningCycle> cycles;

    @BeforeEach
    void setUp() {
        DemandRequirementsResolver resolver = new DemandRequirementsResolver(
                new DemandResourceDerivationService(participatingDirections), profileRequirements);
        service = new PlanningScenarioAnalysisService(scenarioService, items, pool, resolver, dependencies,
                capacityAnalysis, new CapacityCalculator(new CapacityThresholdsProperties()), strategicPlans);

        plan = new StrategicPlan();
        plan.setId(UUID.randomUUID());
        scenario = new PlanningScenario();
        scenario.setId(UUID.randomUUID());
        scenario.setStrategicPlan(plan);
        scenario.setStatus(ScenarioStatus.DRAFT);

        // Tipo com perfis nas fases: Análise (40%) e Desenvolvimento (60%).
        Profile analyst = profile("Analista", "DSI");
        Profile developer = profile("Programador", "DSI");
        Profile technician = profile("Técnico de Rede", "DOM");
        PhaseModel type = new PhaseModel();
        type.setId(UUID.randomUUID());
        PhaseDefinition analysis = phase(type, "Análise", 1, "40");
        PhaseDefinition development = phase(type, "Desenvolvimento", 2, "60");
        assign(analysis, analyst, "100");
        assign(development, developer, "100");
        assign(development, technician, "50");

        cycles = List.of(
                cycle("DEM-DSI-1", "DSI", type),
                cycle("DEM-DSI-2", "DSI", type),
                cycle("DEM-DOM-1", "DOM", type));

        when(strategicPlans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(scenarioService.requireScenario(scenario.getId())).thenReturn(scenario);
        when(scenarioService.listCriteria(scenario.getId())).thenReturn(List.of());
        when(capacityAnalysis.statusesForStrategicPlan(plan.getId())).thenReturn(Map.of());
        when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(pool.portfolioPool(plan.getId())).thenReturn(cycles.stream()
                .map(cycle -> new ScenarioDemandSnapshot(cycle, 0, 0)).toList());
    }

    @Test
    void planAnalysisReturnsDerivedProfilesOfAllIncludedDemands() {
        assertProfilesFromAllDemands(service.planAnalysis(plan.getId()).capacity());
    }

    @Test
    void scenarioAnalysisReturnsDerivedProfilesOfAllIncludedDemands() {
        when(items.findByScenario_Id(scenario.getId())).thenReturn(cycles.stream().map(this::included).toList());

        assertProfilesFromAllDemands(service.analysis(scenario.getId()).capacity());
    }

    private void assertProfilesFromAllDemands(CapacityAnalysis capacity) {
        assertThat(capacity.profiles()).extracting(ProfileCapacityInfo::profileName)
                .containsExactlyInAnyOrder("Analista", "Programador", "Técnico de Rede");
        assertThat(capacity.profiles()).allSatisfy(profile -> {
            assertThat(profile.peakUtilization()).isNotNull();
            assertThat(profile.requiredLoadAtPeak()).isPositive();
        });
        // Duas Demandas DSI em paralelo: 2 Programadores sobre 2 disponíveis = 100%.
        assertThat(capacity.profiles()).filteredOn(p -> p.profileName().equals("Programador"))
                .singleElement().extracting(ProfileCapacityInfo::peakUtilization)
                .satisfies(peak -> assertThat(peak).isEqualByComparingTo("100.00"));
        assertThat(capacity.peakUtilization()).isNotNull();
        assertThat(capacity.totalRequiredLoad()).isPositive();
        verify(profileRequirements, never()).findByDemand_IdIn(any());
    }

    private DemandPlanningCycle cycle(String code, String directionCode, PhaseModel type) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setCode(code);
        demand.setTitle(code);
        demand.setNature(DemandNature.NORMAL);
        demand.setDirectionCode(directionCode);
        demand.setType(type);
        demand.setPlannedStartDate(LocalDate.of(2027, 1, 1));
        demand.setDesiredDate(LocalDate.of(2027, 10, 31));

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(demand);
        cycle.setStrategicPlan(plan);
        cycle.setStatus(DemandPlanningStatus.PRIORITIZED);
        return cycle;
    }

    private PlanningScenarioItem included(DemandPlanningCycle cycle) {
        PlanningScenarioItem item = new PlanningScenarioItem();
        item.setId(UUID.randomUUID());
        item.setScenario(scenario);
        item.setDemandPlanningCycle(cycle);
        item.setIncluded(true);
        return item;
    }

    private static Profile profile(String name, String directionCode) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);
        profile.setDirectionCode(directionCode);
        profile.setAvailableCapacity(2);
        profile.setSimultaneousDemandCapacity(2);
        profile.setActive(true);
        return profile;
    }

    private static PhaseDefinition phase(PhaseModel type, String name, int sequence, String weight) {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setId(UUID.randomUUID());
        phase.setPhaseModel(type);
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal(weight));
        type.getPhases().add(phase);
        return phase;
    }

    private static void assign(PhaseDefinition phase, Profile profile, String allocation) {
        PhaseProfile phaseProfile = new PhaseProfile();
        phaseProfile.setId(UUID.randomUUID());
        phaseProfile.setPhaseDefinition(phase);
        phaseProfile.setProfile(profile);
        phaseProfile.setAllocationPercentage(new BigDecimal(allocation));
        phase.getProfiles().add(phaseProfile);
    }
}
