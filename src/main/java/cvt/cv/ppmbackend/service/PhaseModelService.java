package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PhaseModelDtos.DirectionRef;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelResponse;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseProfileRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseProfileResponse;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseResponse;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ReorderRequest;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.PhaseModelDirection;
import cvt.cv.ppmbackend.entity.PhaseProfile;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.PhaseDefinitionRepository;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import cvt.cv.ppmbackend.repository.PhaseProfileRepository;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class PhaseModelService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.01");

    private final PhaseModelRepository models;
    private final PhaseDefinitionRepository phases;
    private final PhaseProfileRepository phaseProfiles;
    private final ProfileRepository profiles;

    public PhaseModelService(PhaseModelRepository models, PhaseDefinitionRepository phases,
            PhaseProfileRepository phaseProfiles, ProfileRepository profiles) {
        this.models = models;
        this.phases = phases;
        this.phaseProfiles = phaseProfiles;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<ModelResponse> list(Boolean activeOnly) {
        List<PhaseModel> rows = Boolean.TRUE.equals(activeOnly)
                ? models.findAllByActiveOrderByNameAsc(true)
                : models.findAllByOrderByNameAsc();
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ModelResponse get(UUID id) {
        return toResponse(require(id));
    }

    public ModelResponse create(ModelRequest request) {
        String normalizedCode = normalizeCode(request.code());
        ensureUniqueModelCode(normalizedCode, null);

        PhaseModel model = new PhaseModel();
        apply(request, model, normalizedCode);
        return toResponse(persist(model));
    }

    public ModelResponse update(UUID id, ModelRequest request) {
        PhaseModel model = require(id);
        String normalizedCode = normalizeCode(request.code());
        ensureUniqueModelCode(normalizedCode, id);

        apply(request, model, normalizedCode);
        return toResponse(persist(model));
    }

    public ModelResponse setActive(UUID id, boolean active) {
        PhaseModel model = require(id);
        if (model.isActive() == active) {
            return toResponse(model);
        }
        model.setActive(active);
        if (active) {
            validateWeights(model);
        }
        return toResponse(persist(model));
    }

    public void delete(UUID id) {
        models.delete(require(id));
    }

    public PhaseResponse addPhase(UUID modelId, PhaseRequest request) {
        PhaseModel model = require(modelId);
        String code = normalizeCode(request.code());
        ensureUniquePhaseCode(model.getId(), code, null);

        PhaseDefinition phase = new PhaseDefinition();
        phase.setPhaseModel(model);
        applyPhase(request, phase, code, nextSequence(model, request.sequence()));
        model.getPhases().add(phase);

        if (model.isActive()) {
            validateWeights(model);
        }
        PhaseDefinition saved = phases.save(phase);
        return toPhaseResponse(saved);
    }

    public PhaseResponse updatePhase(UUID modelId, UUID phaseId, PhaseRequest request) {
        PhaseModel model = require(modelId);
        PhaseDefinition phase = requirePhase(model, phaseId);
        String code = normalizeCode(request.code());
        ensureUniquePhaseCode(model.getId(), code, phaseId);

        Integer sequence = request.sequence() != null ? request.sequence() : phase.getSequence();
        applyPhase(request, phase, code, sequence);

        if (model.isActive()) {
            validateWeights(model);
        }
        return toPhaseResponse(phases.save(phase));
    }

    public void deletePhase(UUID modelId, UUID phaseId) {
        PhaseModel model = require(modelId);
        PhaseDefinition phase = requirePhase(model, phaseId);
        model.getPhases().remove(phase);
        phases.delete(phase);
        if (model.isActive()) {
            validateWeights(model);
        }
    }

    public List<PhaseResponse> reorder(UUID modelId, ReorderRequest request) {
        PhaseModel model = require(modelId);
        List<UUID> order = request.phaseIds();

        Set<UUID> known = new HashSet<>();
        model.getPhases().forEach(p -> known.add(p.getId()));
        if (order.size() != known.size() || !known.containsAll(order) || new HashSet<>(order).size() != order.size()) {
            throw validationError("phaseIds",
                    "A lista de reordenamento deve conter todas as fases do modelo, sem duplicados.");
        }

        Map<UUID, PhaseDefinition> byId = new HashMap<>();
        model.getPhases().forEach(p -> byId.put(p.getId(), p));
        List<PhaseDefinition> reordered = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            PhaseDefinition p = byId.get(order.get(i));
            p.setSequence(i + 1);
            reordered.add(p);
        }
        phases.saveAll(reordered);
        return reordered.stream().map(this::toPhaseResponse).toList();
    }

    public PhaseResponse setPhaseActive(UUID modelId, UUID phaseId, boolean active) {
        PhaseModel model = require(modelId);
        PhaseDefinition phase = requirePhase(model, phaseId);
        if (phase.isActive() == active) {
            return toPhaseResponse(phase);
        }
        phase.setActive(active);
        if (model.isActive()) {
            validateWeights(model);
        }
        return toPhaseResponse(phases.save(phase));
    }

    private PhaseModel require(UUID id) {
        return models.findWithPhasesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo de fases não encontrado: " + id));
    }

    private PhaseDefinition requirePhase(PhaseModel model, UUID phaseId) {
        return model.getPhases().stream()
                .filter(p -> p.getId().equals(phaseId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Fase não encontrada no modelo " + model.getCode() + ": " + phaseId));
    }

    private void apply(ModelRequest request, PhaseModel model, String code) {
        model.setCode(code);
        model.setName(request.name().trim());
        model.setDescription(request.description() == null ? null : request.description().trim());
        applyApplicableDirections(request, model);
        if (request.fields() != null) {
            model.setFields(TypeFieldRules.normalizeDefinitions(request.fields()));
        }
        boolean nextActive = request.active() == null ? model.isActive() : request.active();
        if (nextActive && !model.getPhases().isEmpty()) {
            model.setActive(true);
            validateWeights(model);
        } else {
            model.setActive(nextActive);
        }
    }

    private void applyApplicableDirections(ModelRequest request, PhaseModel model) {
        if (request.applicableDirectionCodes() == null) {
            return;
        }
        Set<String> newCodes = new LinkedHashSet<>();
        for (String raw : request.applicableDirectionCodes()) {
            if (raw != null && !raw.isBlank()) {
                newCodes.add(raw.trim().toUpperCase(Locale.ROOT));
            }
        }

        Map<String, PhaseModelDirection> currentByCode = new LinkedHashMap<>();
        for (PhaseModelDirection direction : model.getApplicableDirections()) {
            currentByCode.put(direction.getDirectionCode().toUpperCase(Locale.ROOT), direction);
        }

        Set<String> removedCodes = new LinkedHashSet<>(currentByCode.keySet());
        removedCodes.removeAll(newCodes);
        if (!removedCodes.isEmpty() && model.getId() != null) {
            ensureDirectionsHaveNoProfiles(model, removedCodes);
        }

        Map<String, String> names = resolveDirectionNames(newCodes);
        model.getApplicableDirections().removeIf(d -> !newCodes.contains(d.getDirectionCode().toUpperCase(Locale.ROOT)));
        for (String directionCode : newCodes) {
            PhaseModelDirection existing = currentByCode.get(directionCode);
            if (existing != null) {
                existing.setDirectionName(names.get(directionCode));
            } else {
                PhaseModelDirection direction = new PhaseModelDirection();
                direction.setPhaseModel(model);
                direction.setDirectionCode(directionCode);
                direction.setDirectionName(names.get(directionCode));
                model.getApplicableDirections().add(direction);
            }
        }
    }

    private void ensureDirectionsHaveNoProfiles(PhaseModel model, Set<String> removedCodes) {
        Set<String> blocking = new LinkedHashSet<>();
        for (PhaseProfile phaseProfile : phaseProfiles.findByPhaseModelId(model.getId())) {
            String directionCode = phaseProfile.getProfile().getDirectionCode();
            if (directionCode != null && removedCodes.contains(directionCode.trim().toUpperCase(Locale.ROOT))) {
                blocking.add(directionCode.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (!blocking.isEmpty()) {
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DIRECTION_HAS_PHASE_PROFILES",
                    "Não é possível retirar as direções " + String.join(", ", blocking)
                            + " porque ainda têm perfis associados a fases deste modelo.",
                    Map.of("field", "applicableDirectionCodes",
                            "directions", List.copyOf(blocking)));
        }
    }

    private Map<String, String> resolveDirectionNames(Set<String> codes) {
        Map<String, String> names = new HashMap<>();
        if (codes.isEmpty()) {
            return names;
        }
        List<String> lower = codes.stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
        for (Profile profile : profiles.findByDirectionCodeInIgnoreCase(lower)) {
            if (profile.getDirectionCode() == null) {
                continue;
            }
            String key = profile.getDirectionCode().trim().toUpperCase(Locale.ROOT);
            if (profile.getDirectionName() != null && !profile.getDirectionName().isBlank()) {
                names.putIfAbsent(key, profile.getDirectionName().trim());
            }
        }
        return names;
    }

    // ----- Perfis por fase -----

    public PhaseProfileResponse addPhaseProfile(UUID modelId, UUID phaseId, PhaseProfileRequest request) {
        PhaseModel model = require(modelId);
        PhaseDefinition phase = requirePhase(model, phaseId);
        Profile profile = loadProfile(request.profileId());
        validateProfileForModel(model, profile);

        if (phaseProfiles.existsByPhaseDefinitionIdAndProfile_Id(phaseId, profile.getId())) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "PHASE_PROFILE_CONFLICT",
                    "O perfil '" + profile.getName() + "' já está associado a esta fase.",
                    Map.of("field", "profileId"));
        }

        PhaseProfile phaseProfile = new PhaseProfile();
        phaseProfile.setPhaseDefinition(phase);
        phaseProfile.setProfile(profile);
        phaseProfile.setAllocationPercentage(request.allocationPercentage().setScale(2, RoundingMode.HALF_UP));
        phase.getProfiles().add(phaseProfile);
        return toPhaseProfileResponse(persistPhaseProfile(phaseProfile));
    }

    public PhaseProfileResponse updatePhaseProfile(UUID modelId, UUID phaseId, UUID phaseProfileId,
            PhaseProfileRequest request) {
        PhaseModel model = require(modelId);
        PhaseDefinition phase = requirePhase(model, phaseId);
        PhaseProfile phaseProfile = requirePhaseProfile(phase, phaseProfileId);
        Profile profile = loadProfile(request.profileId());
        validateProfileForModel(model, profile);

        if (!profile.getId().equals(phaseProfile.getProfile().getId())
                && phaseProfiles.existsByPhaseDefinitionIdAndProfile_Id(phaseId, profile.getId())) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "PHASE_PROFILE_CONFLICT",
                    "O perfil '" + profile.getName() + "' já está associado a esta fase.",
                    Map.of("field", "profileId"));
        }

        phaseProfile.setProfile(profile);
        phaseProfile.setAllocationPercentage(request.allocationPercentage().setScale(2, RoundingMode.HALF_UP));
        return toPhaseProfileResponse(persistPhaseProfile(phaseProfile));
    }

    public void deletePhaseProfile(UUID modelId, UUID phaseId, UUID phaseProfileId) {
        PhaseModel model = require(modelId);
        PhaseDefinition phase = requirePhase(model, phaseId);
        PhaseProfile phaseProfile = requirePhaseProfile(phase, phaseProfileId);
        phase.getProfiles().remove(phaseProfile);
        phaseProfiles.delete(phaseProfile);
    }

    private void validateProfileForModel(PhaseModel model, Profile profile) {
        if (!profile.isActive()) {
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PROFILE_INACTIVE",
                    "O perfil '" + profile.getName() + "' está inativo e não pode ser associado a fases.",
                    Map.of("field", "profileId"));
        }
        Set<String> applicable = model.getApplicableDirections().stream()
                .map(d -> d.getDirectionCode().trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        String directionCode = profile.getDirectionCode() == null
                ? null : profile.getDirectionCode().trim().toUpperCase(Locale.ROOT);
        if (directionCode == null || !applicable.contains(directionCode)) {
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PROFILE_DIRECTION_NOT_APPLICABLE",
                    "O perfil '" + profile.getName() + "' pertence a uma direção que não está entre as direções"
                            + " aplicáveis deste modelo.",
                    Map.of("field", "profileId",
                            "directionCode", directionCode == null ? "null" : directionCode));
        }
    }

    private Profile loadProfile(UUID profileId) {
        return profiles.findById(profileId)
                .orElseThrow(() -> new BadRequestException("Perfil não encontrado: " + profileId));
    }

    private PhaseProfile requirePhaseProfile(PhaseDefinition phase, UUID phaseProfileId) {
        return phase.getProfiles().stream()
                .filter(pp -> pp.getId().equals(phaseProfileId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de fase não encontrado na fase " + phase.getCode() + ": " + phaseProfileId));
    }

    private PhaseProfile persistPhaseProfile(PhaseProfile phaseProfile) {
        try {
            return phaseProfiles.saveAndFlush(phaseProfile);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "PHASE_PROFILE_CONFLICT",
                    "O perfil já está associado a esta fase.",
                    Map.of("field", "profileId"));
        }
    }

    private PhaseProfileResponse toPhaseProfileResponse(PhaseProfile phaseProfile) {
        Profile profile = phaseProfile.getProfile();
        return new PhaseProfileResponse(
                phaseProfile.getId(),
                profile.getId(),
                profile.getName(),
                profile.getDirectionCode(),
                profile.getDirectionName(),
                phaseProfile.getAllocationPercentage(),
                profile.isActive());
    }

    private void applyPhase(PhaseRequest request, PhaseDefinition phase, String code, Integer sequence) {
        phase.setCode(code);
        phase.setName(request.name().trim());
        phase.setDescription(request.description() == null ? null : request.description().trim());
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(request.durationWeightPercentage().setScale(2, RoundingMode.HALF_UP));
        if (request.active() != null) {
            phase.setActive(request.active());
        }
    }

    private Integer nextSequence(PhaseModel model, Integer requested) {
        if (requested != null) {
            return requested;
        }
        return model.getPhases().stream()
                .map(PhaseDefinition::getSequence)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(max -> max + 1)
                .orElse(1);
    }

    private void validateWeights(PhaseModel model) {
        BigDecimal total = sumActiveWeights(model);
        if (total.subtract(ONE_HUNDRED).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PHASE_MODEL_WEIGHTS_INVALID",
                    "A soma dos pesos das fases ativas deve ser 100%. Atual: " + total.stripTrailingZeros().toPlainString() + "%.",
                    Map.of("field", "durationWeightPercentage",
                            "currentSum", total.toPlainString()));
        }
    }

    private BigDecimal sumActiveWeights(PhaseModel model) {
        return model.getPhases().stream()
                .filter(PhaseDefinition::isActive)
                .map(PhaseDefinition::getDurationWeightPercentage)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean weightsBalanced(PhaseModel model) {
        if (model.getPhases().stream().noneMatch(PhaseDefinition::isActive)) {
            return false;
        }
        return sumActiveWeights(model).subtract(ONE_HUNDRED).abs().compareTo(WEIGHT_TOLERANCE) <= 0;
    }

    private void ensureUniqueModelCode(String code, UUID currentId) {
        boolean exists = currentId == null
                ? models.existsByCodeIgnoreCase(code)
                : models.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (exists) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "PHASE_MODEL_CODE_CONFLICT",
                    "Já existe um modelo de fases com o código '" + code + "'.",
                    Map.of("field", "code"));
        }
    }

    private void ensureUniquePhaseCode(UUID modelId, String code, UUID phaseId) {
        boolean exists = phaseId == null
                ? phases.existsByPhaseModelIdAndCodeIgnoreCase(modelId, code)
                : phases.existsByPhaseModelIdAndCodeIgnoreCaseAndIdNot(modelId, code, phaseId);
        if (exists) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "PHASE_CODE_CONFLICT",
                    "Já existe uma fase com o código '" + code + "' neste modelo.",
                    Map.of("field", "code"));
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("code é obrigatório");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private PhaseModel persist(PhaseModel model) {
        try {
            return models.saveAndFlush(model);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "PHASE_MODEL_CODE_CONFLICT",
                    "Conflito ao gravar o modelo de fases '" + model.getCode() + "'.",
                    Map.of("field", "code"));
        }
    }

    private DomainException validationError(String field, String message) {
        return new DomainException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                message,
                Map.of("field", field));
    }

    private ModelResponse toResponse(PhaseModel model) {
        List<PhaseDefinition> ordered = new ArrayList<>(model.getPhases());
        ordered.sort(Comparator.comparing(PhaseDefinition::getSequence,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<DirectionRef> directions = model.getApplicableDirections().stream()
                .sorted(Comparator.comparing(PhaseModelDirection::getDirectionCode,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> new DirectionRef(d.getDirectionCode(), d.getDirectionName()))
                .toList();
        return new ModelResponse(
                model.getId(),
                model.getCode(),
                model.getName(),
                model.getDescription(),
                model.isActive(),
                sumActiveWeights(model),
                weightsBalanced(model),
                directions,
                ordered.stream().map(this::toPhaseResponse).toList(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                TypeFieldRules.definitions(model));
    }

    private PhaseResponse toPhaseResponse(PhaseDefinition phase) {
        List<PhaseProfileResponse> profileResponses = phase.getProfiles().stream()
                .sorted(Comparator.comparing(pp -> pp.getProfile().getName(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toPhaseProfileResponse)
                .toList();
        return new PhaseResponse(
                phase.getId(),
                phase.getPhaseModel().getId(),
                phase.getCode(),
                phase.getName(),
                phase.getDescription(),
                phase.getSequence(),
                phase.getDurationWeightPercentage(),
                phase.isActive(),
                profileResponses,
                phase.getCreatedAt(),
                phase.getUpdatedAt());
    }
}
