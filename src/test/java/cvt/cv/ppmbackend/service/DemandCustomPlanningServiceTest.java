package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import cvt.cv.ppmbackend.dto.DemandDtos.CustomPlanningResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandCustomPlanningServiceTest {

    @Mock
    private ProfileRepository profiles;
    @Mock
    private PhaseModelRepository phaseModels;
    @Mock
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DemandCustomPlanningService service;

    private final UUID baseTypeId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DemandCustomPlanningService(objectMapper, profiles, phaseModels, entityManager);
        lenient().when(phaseModels.existsById(baseTypeId)).thenReturn(true);
    }

    @Test
    void appliesValidCustomPlanningAndBuildsEnrichedResponse() {
        Profile backend = profile("Backend Developer", "DSI", "Direção de Sistemas", true);
        when(profiles.findById(profileId)).thenReturn(Optional.of(backend));

        Demand demand = new Demand();
        service.apply(demand, planning(phase("Desenvolvimento", 4, 100, resource(profileId, 150))), "tester");

        assertThat(demand.getCustomPhases()).hasSize(1);
        DemandCustomPhase phase = demand.getCustomPhases().get(0);
        assertThat(phase.getName()).isEqualTo("Desenvolvimento");
        assertThat(phase.getDurationWeightPercentage()).isEqualByComparingTo("100.00");
        assertThat(phase.getResources()).hasSize(1);
        assertThat(phase.getResources().get(0).getAllocationPercentage()).isEqualByComparingTo("150.00");
        assertThat(demand.getCustomPlanningBaseTypeId()).isEqualTo(baseTypeId);

        CustomPlanningResponse response = service.buildResponse(demand);
        assertThat(response).isNotNull();
        assertThat(response.baseTypeId()).isEqualTo(baseTypeId);
        assertThat(response.phases()).hasSize(1);
        var resourceResponse = response.phases().get(0).resources().get(0);
        assertThat(resourceResponse.profileName()).isEqualTo("Backend Developer");
        assertThat(resourceResponse.directionCode()).isEqualTo("DSI");
        assertThat(resourceResponse.directionName()).isEqualTo("Direção de Sistemas");
        assertThat(resourceResponse.profileActive()).isTrue();
    }

    @Test
    void activeWeightsNotHundredAreRejected() {
        Demand demand = new Demand();
        JsonNode node = planning(
                phase("Fase A", 1, 60),
                phase("Fase B", 2, 50));

        assertThatThrownBy(() -> service.apply(demand, node, "tester"))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("CUSTOM_PLANNING_INVALID");
    }

    @Test
    void requiresAtLeastOneActivePhase() {
        Demand demand = new Demand();
        JsonNode node = planning(inactivePhase("Fase A", 1, 100));

        assertThatThrownBy(() -> service.apply(demand, node, "tester"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsInactiveProfile() {
        Profile inactive = profile("Antigo", "DSI", "Direção", false);
        when(profiles.findById(profileId)).thenReturn(Optional.of(inactive));
        Demand demand = new Demand();
        JsonNode node = planning(phase("Dev", 1, 100, resource(profileId, 100)));

        assertThatThrownBy(() -> service.apply(demand, node, "tester"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsDuplicateProfileWithinPhase() {
        Demand demand = new Demand();
        JsonNode node = planning(phase("Dev", 1, 100,
                resource(profileId, 100), resource(profileId, 50)));

        assertThatThrownBy(() -> service.apply(demand, node, "tester"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsAllocationOutOfRange() {
        Demand demand = new Demand();
        JsonNode node = planning(phase("Dev", 1, 100, resource(profileId, 20000)));

        assertThatThrownBy(() -> service.apply(demand, node, "tester"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void explicitNullNodeClearsCustomPlanning() {
        Demand demand = new Demand();
        DemandCustomPhase existing = new DemandCustomPhase();
        existing.setDemand(demand);
        existing.setName("Antiga");
        existing.setSequence(1);
        demand.getCustomPhases().add(existing);
        demand.setCustomPlanningBaseTypeId(baseTypeId);

        service.apply(demand, NullNode.getInstance(), "tester");

        assertThat(demand.getCustomPhases()).isEmpty();
        assertThat(demand.getCustomPlanningBaseTypeId()).isNull();
        assertThat(service.buildResponse(demand)).isNull();
    }

    @Test
    void omittedNodeDoesNotTouchPlanning() {
        Demand demand = new Demand();
        DemandCustomPhase existing = new DemandCustomPhase();
        existing.setName("Antiga");
        existing.setSequence(1);
        demand.getCustomPhases().add(existing);

        service.apply(demand, null, "tester");

        assertThat(demand.getCustomPhases()).hasSize(1);
    }

    private JsonNode planning(Map<String, Object>... phases) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("baseTypeId", baseTypeId.toString());
        root.put("phases", List.of(phases));
        return objectMapper.valueToTree(root);
    }

    private Map<String, Object> phase(String name, int sequence, int weight, Map<String, Object>... resources) {
        return phaseNode(name, sequence, weight, true, resources);
    }

    private Map<String, Object> inactivePhase(String name, int sequence, int weight, Map<String, Object>... resources) {
        return phaseNode(name, sequence, weight, false, resources);
    }

    private Map<String, Object> phaseNode(String name, int sequence, int weight, boolean active,
            Map<String, Object>... resources) {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("id", null);
        phase.put("sourcePhaseId", null);
        phase.put("name", name);
        phase.put("description", null);
        phase.put("sequence", sequence);
        phase.put("durationWeightPercentage", weight);
        phase.put("active", active);
        phase.put("resources", new ArrayList<>(List.of(resources)));
        return phase;
    }

    private Map<String, Object> resource(UUID profileId, int allocation) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", null);
        resource.put("profileId", profileId.toString());
        resource.put("allocationPercentage", allocation);
        return resource;
    }

    private Profile profile(String name, String directionCode, String directionName, boolean active) {
        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setName(name);
        profile.setDirectionCode(directionCode);
        profile.setDirectionName(directionName);
        profile.setActive(active);
        return profile;
    }
}
