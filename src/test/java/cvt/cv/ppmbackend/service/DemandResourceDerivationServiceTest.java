package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.DemandCustomResource;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.PhaseProfile;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandResourceDerivationServiceTest {

    @Mock
    private DemandParticipatingDirectionRepository participatingDirections;

    private DemandResourceDerivationService service;

    @BeforeEach
    void setUp() {
        service = new DemandResourceDerivationService(participatingDirections);
    }

    @Test
    void derivesProfileFteFromTypePhaseProfilesForInvolvedDirection() {
        Profile admin = profile("Administrador de Sistemas", "DSI", true);
        PhaseModel type = phaseModel("PROJ_TI");
        PhaseDefinition analise = phase(type, "ANALISE", "Análise", true);
        addPhaseProfile(analise, admin, "150.00");

        Demand demand = demand("DSI", type);
        when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(demand.getId())).thenReturn(List.of());

        List<DemandProfileRequirement> derived = service.deriveRequirements(demand);

        assertThat(derived).hasSize(1);
        DemandProfileRequirement req = derived.get(0);
        assertThat(req.getProfile().getName()).isEqualTo("Administrador de Sistemas");
        assertThat(req.getEquivalentLoad()).isEqualByComparingTo("1.5");
        assertThat(req.getPhases()).extracting(PhaseDefinition::getCode).containsExactly("ANALISE");

        DemandResourceDerivationService.ResourceSummary summary = service.summary(demand);
        assertThat(summary.profilesCount()).isEqualTo(1);
        assertThat(summary.totalFte()).isEqualByComparingTo("1.50");
    }

    @Test
    void ignoresProfilesOfDirectionsNotInvolvedInTheDemand() {
        Profile admin = profile("Administrador de Sistemas", "DSI", true);
        PhaseModel type = phaseModel("PROJ_TI");
        PhaseDefinition analise = phase(type, "ANALISE", "Análise", true);
        addPhaseProfile(analise, admin, "150.00");

        Demand demand = demand("DFI", type);
        when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(demand.getId())).thenReturn(List.of());

        assertThat(service.deriveRequirements(demand)).isEmpty();
        assertThat(service.hasTypeResources(demand)).isTrue();
    }

    @Test
    void ignoresInactivePhasesAndInactiveProfiles() {
        Profile inactive = profile("Perfil Inativo", "DSI", false);
        PhaseModel type = phaseModel("PROJ_TI");
        PhaseDefinition inactivePhase = phase(type, "FECHO", "Fecho", false);
        addPhaseProfile(inactivePhase, profile("Gestor", "DSI", true), "100.00");
        PhaseDefinition analise = phase(type, "ANALISE", "Análise", true);
        addPhaseProfile(analise, inactive, "100.00");

        Demand demand = demand("DSI", type);
        when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(demand.getId())).thenReturn(List.of());

        assertThat(service.deriveRequirements(demand)).isEmpty();
    }

    @Test
    void customPlanningOverridesTypeAndIgnoresDirectionFilter() {
        Profile backend = profile("Backend Developer", "DSI", true);
        PhaseModel type = phaseModel("PROJ_TI");
        PhaseDefinition analise = phase(type, "ANALISE", "Análise", true);
        addPhaseProfile(analise, profile("Administrador", "DSI", true), "100.00");

        Demand demand = demand("DFI", type);
        DemandCustomPhase dev = customPhase(demand, "Desenvolvimento", 4, "100.00", true);
        addCustomResource(dev, backend, "150.00");

        assertThat(service.hasCustomPlanning(demand)).isTrue();
        assertThat(service.hasDerivedResources(demand)).isTrue();

        List<DemandProfileRequirement> derived = service.deriveRequirements(demand);
        assertThat(derived).hasSize(1);
        assertThat(derived.get(0).getProfile().getName()).isEqualTo("Backend Developer");
        assertThat(derived.get(0).getEquivalentLoad()).isEqualByComparingTo("1.5");

        DemandResourceDerivationService.ResourceSummary summary = service.summary(demand);
        assertThat(summary.profilesCount()).isEqualTo(1);
        assertThat(summary.totalFte()).isEqualByComparingTo("1.50");
    }

    private DemandCustomPhase customPhase(Demand demand, String name, int sequence, String weight, boolean active) {
        DemandCustomPhase phase = new DemandCustomPhase();
        phase.setId(UUID.randomUUID());
        phase.setDemand(demand);
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal(weight));
        phase.setActive(active);
        demand.getCustomPhases().add(phase);
        return phase;
    }

    private void addCustomResource(DemandCustomPhase phase, Profile profile, String allocation) {
        DemandCustomResource resource = new DemandCustomResource();
        resource.setId(UUID.randomUUID());
        resource.setCustomPhase(phase);
        resource.setProfile(profile);
        resource.setAllocationPercentage(new BigDecimal(allocation));
        phase.getResources().add(resource);
    }

    private Demand demand(String directionCode, PhaseModel type) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setDirectionCode(directionCode);
        demand.setType(type);
        return demand;
    }

    private PhaseModel phaseModel(String code) {
        PhaseModel model = new PhaseModel();
        model.setId(UUID.randomUUID());
        model.setCode(code);
        model.setName(code);
        return model;
    }

    private PhaseDefinition phase(PhaseModel model, String code, String name, boolean active) {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setId(UUID.randomUUID());
        phase.setPhaseModel(model);
        phase.setCode(code);
        phase.setName(name);
        phase.setSequence(model.getPhases().size() + 1);
        phase.setDurationWeightPercentage(new BigDecimal("100.00"));
        phase.setActive(active);
        model.getPhases().add(phase);
        return phase;
    }

    private void addPhaseProfile(PhaseDefinition phase, Profile profile, String allocation) {
        PhaseProfile phaseProfile = new PhaseProfile();
        phaseProfile.setId(UUID.randomUUID());
        phaseProfile.setPhaseDefinition(phase);
        phaseProfile.setProfile(profile);
        phaseProfile.setAllocationPercentage(new BigDecimal(allocation));
        phase.getProfiles().add(phaseProfile);
    }

    private Profile profile(String name, String directionCode, boolean active) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);
        profile.setDirectionCode(directionCode);
        profile.setDirectionName(directionCode);
        profile.setActive(active);
        return profile;
    }
}
