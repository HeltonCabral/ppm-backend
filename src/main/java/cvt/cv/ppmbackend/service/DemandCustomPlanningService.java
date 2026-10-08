package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.dto.DemandDtos.CustomPhaseInput;
import cvt.cv.ppmbackend.dto.DemandDtos.CustomPhaseResponse;
import cvt.cv.ppmbackend.dto.DemandDtos.CustomPlanningInput;
import cvt.cv.ppmbackend.dto.DemandDtos.CustomPlanningResponse;
import cvt.cv.ppmbackend.dto.DemandDtos.CustomResourceInput;
import cvt.cv.ppmbackend.dto.DemandDtos.CustomResourceResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.DemandCustomResource;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gestão do planeamento personalizado de uma Demanda (fases + recursos). O planeamento, quando
 * presente, substitui o do Tipo sem o alterar. Responsável por validar (422), persistir e construir
 * a resposta {@code customPlanning}.
 */
@Service
@Transactional
public class DemandCustomPlanningService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal MAX_ALLOCATION = new BigDecimal("10000");

    private final ObjectMapper objectMapper;
    private final ProfileRepository profiles;
    private final PhaseModelRepository phaseModels;
    private final EntityManager entityManager;

    public DemandCustomPlanningService(ObjectMapper objectMapper, ProfileRepository profiles,
            PhaseModelRepository phaseModels, EntityManager entityManager) {
        this.objectMapper = objectMapper;
        this.profiles = profiles;
        this.phaseModels = phaseModels;
        this.entityManager = entityManager;
    }

    /**
     * Aplica o campo {@code customPlanning} do pedido com semântica tri-estado:
     * {@code null} (omitido) não mexe; nó JSON {@code null} apaga; objeto substitui.
     */
    public void apply(Demand demand, JsonNode node, String actor) {
        if (node == null) {
            return;
        }
        if (node.isNull()) {
            clear(demand);
            return;
        }
        CustomPlanningInput input = parse(node);
        replace(demand, input);
    }

    /** Remove a personalização: a Demanda volta a seguir o planeamento do Tipo. */
    public void clear(Demand demand) {
        if (!demand.getCustomPhases().isEmpty()) {
            demand.getCustomPhases().clear();
        }
        demand.setCustomPlanningBaseTypeId(null);
        entityManager.flush();
    }

    /** {@code true} quando a Demanda tem planeamento personalizado. */
    @Transactional(readOnly = true)
    public boolean hasCustomPlanning(Demand demand) {
        return demand != null && demand.getCustomPhases() != null && !demand.getCustomPhases().isEmpty();
    }

    @Transactional(readOnly = true)
    public CustomPlanningResponse buildResponse(Demand demand) {
        if (!hasCustomPlanning(demand)) {
            return null;
        }
        List<CustomPhaseResponse> phases = demand.getCustomPhases().stream()
                .sorted(Comparator.comparing(DemandCustomPhase::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toPhaseResponse)
                .toList();
        return new CustomPlanningResponse(demand.getCustomPlanningBaseTypeId(), phases);
    }

    private CustomPhaseResponse toPhaseResponse(DemandCustomPhase phase) {
        List<CustomResourceResponse> resources = phase.getResources().stream()
                .map(this::toResourceResponse)
                .toList();
        return new CustomPhaseResponse(phase.getId(), phase.getSourcePhaseId(), phase.getName(),
                phase.getDescription(), phase.getSequence(), phase.getDurationWeightPercentage(),
                phase.isActive(), resources);
    }

    private CustomResourceResponse toResourceResponse(DemandCustomResource resource) {
        Profile profile = resource.getProfile();
        return new CustomResourceResponse(resource.getId(), profile.getId(), resource.getAllocationPercentage(),
                profile.getName(), profile.getDirectionCode(), profile.getDirectionName(), profile.isActive());
    }

    private CustomPlanningInput parse(JsonNode node) {
        try {
            return objectMapper.convertValue(node, CustomPlanningInput.class);
        } catch (IllegalArgumentException exception) {
            throw invalid("customPlanning", "O planeamento personalizado é inválido.");
        }
    }

    private void replace(Demand demand, CustomPlanningInput input) {
        validate(input);
        demand.getCustomPhases().clear();
        List<DemandCustomPhase> phases = buildPhases(demand, input.phases());
        demand.getCustomPhases().addAll(phases);
        demand.setCustomPlanningBaseTypeId(input.baseTypeId());
        entityManager.flush();
    }

    private List<DemandCustomPhase> buildPhases(Demand demand, List<CustomPhaseInput> inputs) {
        List<DemandCustomPhase> phases = new ArrayList<>();
        int order = 1;
        for (CustomPhaseInput input : inputs) {
            DemandCustomPhase phase = new DemandCustomPhase();
            phase.setDemand(demand);
            phase.setSourcePhaseId(input.sourcePhaseId());
            phase.setName(input.name().trim());
            phase.setDescription(input.description() == null || input.description().isBlank()
                    ? null : input.description().trim());
            phase.setSequence(input.sequence() != null ? input.sequence() : order);
            phase.setDurationWeightPercentage(input.durationWeightPercentage().setScale(2, RoundingMode.HALF_UP));
            phase.setActive(input.active() == null || input.active());
            if (input.resources() != null) {
                for (CustomResourceInput resourceInput : input.resources()) {
                    phase.getResources().add(buildResource(phase, resourceInput));
                }
            }
            phases.add(phase);
            order++;
        }
        return phases;
    }

    private DemandCustomResource buildResource(DemandCustomPhase phase, CustomResourceInput input) {
        Profile profile = profiles.findById(input.profileId())
                .orElseThrow(() -> invalid("resources.profileId",
                        "Perfil não encontrado: " + input.profileId()));
        if (!profile.isActive()) {
            throw invalid("resources.profileId", "O perfil selecionado deve estar ativo.");
        }
        DemandCustomResource resource = new DemandCustomResource();
        resource.setCustomPhase(phase);
        resource.setProfile(profile);
        resource.setAllocationPercentage(input.allocationPercentage().setScale(2, RoundingMode.HALF_UP));
        return resource;
    }

    private void validate(CustomPlanningInput input) {
        if (input == null) {
            throw invalid("customPlanning", "O planeamento personalizado é inválido.");
        }
        if (input.baseTypeId() == null) {
            throw invalid("baseTypeId", "baseTypeId é obrigatório.");
        }
        if (!phaseModels.existsById(input.baseTypeId())) {
            throw invalid("baseTypeId", "Tipo de origem não encontrado: " + input.baseTypeId());
        }
        List<CustomPhaseInput> phases = input.phases();
        if (phases == null || phases.isEmpty()) {
            throw invalid("phases", "É obrigatória pelo menos uma fase.");
        }
        boolean hasActive = false;
        BigDecimal activeSum = BigDecimal.ZERO;
        for (CustomPhaseInput phase : phases) {
            if (phase.name() == null || phase.name().isBlank()) {
                throw invalid("phases.name", "O nome da fase é obrigatório.");
            }
            BigDecimal weight = phase.durationWeightPercentage();
            if (weight == null) {
                throw invalid("phases.durationWeightPercentage",
                        "O peso de duração da fase é obrigatório.");
            }
            if (weight.scale() > 2) {
                throw invalid("phases.durationWeightPercentage",
                        "O peso de duração deve ter no máximo 2 casas decimais.");
            }
            if (weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(ONE_HUNDRED) > 0) {
                throw invalid("phases.durationWeightPercentage",
                        "O peso de duração deve estar entre 0 e 100.");
            }
            boolean active = phase.active() == null || phase.active();
            if (active) {
                hasActive = true;
                activeSum = activeSum.add(weight);
            }
            validateResources(phase);
        }
        if (!hasActive) {
            throw invalid("phases", "É obrigatória pelo menos uma fase ativa.");
        }
        if (activeSum.subtract(ONE_HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOM_PLANNING_INVALID",
                    "A soma dos pesos das fases ativas deve ser 100%. Atual: "
                            + activeSum.stripTrailingZeros().toPlainString() + "%.",
                    Map.of("field", "phases.durationWeightPercentage",
                            "currentSum", activeSum.toPlainString()));
        }
    }

    private void validateResources(CustomPhaseInput phase) {
        if (phase.resources() == null) {
            return;
        }
        Set<UUID> profileIds = new HashSet<>();
        for (CustomResourceInput resource : phase.resources()) {
            if (resource.profileId() == null) {
                throw invalid("resources.profileId", "profileId é obrigatório.");
            }
            BigDecimal allocation = resource.allocationPercentage();
            if (allocation == null || allocation.compareTo(BigDecimal.ZERO) <= 0) {
                throw invalid("resources.allocationPercentage",
                        "allocationPercentage deve ser maior que 0.");
            }
            if (allocation.compareTo(MAX_ALLOCATION) > 0) {
                throw invalid("resources.allocationPercentage",
                        "allocationPercentage não pode exceder 10000.");
            }
            if (!profileIds.add(resource.profileId())) {
                throw invalid("resources.profileId",
                        "O mesmo perfil não pode repetir-se na mesma fase.");
            }
        }
    }

    private DomainException invalid(String field, String message) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOM_PLANNING_INVALID",
                message, Map.of("field", field));
    }
}
