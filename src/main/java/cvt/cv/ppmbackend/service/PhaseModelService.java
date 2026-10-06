package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelResponse;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseResponse;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ReorderRequest;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.PhaseDefinitionRepository;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
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

    public PhaseModelService(PhaseModelRepository models, PhaseDefinitionRepository phases) {
        this.models = models;
        this.phases = phases;
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
        boolean nextActive = request.active() == null ? model.isActive() : request.active();
        if (nextActive && !model.getPhases().isEmpty()) {
            model.setActive(true);
            validateWeights(model);
        } else {
            model.setActive(nextActive);
        }
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
        return new ModelResponse(
                model.getId(),
                model.getCode(),
                model.getName(),
                model.getDescription(),
                model.isActive(),
                sumActiveWeights(model),
                weightsBalanced(model),
                ordered.stream().map(this::toPhaseResponse).toList(),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    private PhaseResponse toPhaseResponse(PhaseDefinition phase) {
        return new PhaseResponse(
                phase.getId(),
                phase.getPhaseModel().getId(),
                phase.getCode(),
                phase.getName(),
                phase.getDescription(),
                phase.getSequence(),
                phase.getDurationWeightPercentage(),
                phase.isActive(),
                phase.getCreatedAt(),
                phase.getUpdatedAt());
    }
}
