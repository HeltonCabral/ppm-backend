package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.CriterionConfigRequest;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.CriterionConfigResponse;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.DemandComplexityResponse;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.LevelConfigRequest;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.LevelConfigResponse;
import cvt.cv.ppmbackend.entity.ComplexityCriterionConfig;
import cvt.cv.ppmbackend.entity.ComplexityLevelConfig;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.enums.ComplexityCriterion;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ComplexityCriterionConfigRepository;
import cvt.cv.ppmbackend.repository.ComplexityLevelConfigRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class DemandComplexityService {
    private static final BigDecimal SCORE_STEP = new BigDecimal("0.01");
    private static final BigDecimal MIN_SCORE = new BigDecimal("1.00");
    private static final BigDecimal MAX_SCORE = new BigDecimal("4.00");

    private final ComplexityCriterionConfigRepository criterionConfigs;
    private final ComplexityLevelConfigRepository levelConfigs;
    private final DemandRepository demands;
    private final DemandParticipatingDirectionRepository participatingDirections;
    private final DemandProfileRequirementService profileRequirements;
    private final DemandDependencyRepository dependencies;

    public DemandComplexityService(ComplexityCriterionConfigRepository criterionConfigs,
            ComplexityLevelConfigRepository levelConfigs, DemandRepository demands,
            DemandParticipatingDirectionRepository participatingDirections,
            DemandProfileRequirementService profileRequirements, DemandDependencyRepository dependencies) {
        this.criterionConfigs = criterionConfigs;
        this.levelConfigs = levelConfigs;
        this.demands = demands;
        this.participatingDirections = participatingDirections;
        this.profileRequirements = profileRequirements;
        this.dependencies = dependencies;
    }

    @Transactional(readOnly = true)
    public List<CriterionConfigResponse> getCriteria() {
        Map<ComplexityCriterion, ComplexityCriterionConfig> byCriterion = criterionConfigs.findAll().stream()
                .collect(Collectors.toMap(ComplexityCriterionConfig::getCriterion, Function.identity()));
        return java.util.Arrays.stream(ComplexityCriterion.values())
                .filter(byCriterion::containsKey)
                .map(byCriterion::get)
                .map(CriterionConfigResponse::from)
                .toList();
    }

    public List<CriterionConfigResponse> updateCriteria(List<CriterionConfigRequest> requests) {
        Map<ComplexityCriterion, CriterionConfigRequest> byCriterion = requireAllCriteria(requests);
        Map<ComplexityCriterion, ComplexityCriterionConfig> existing = criterionConfigs.findAll().stream()
                .collect(Collectors.toMap(ComplexityCriterionConfig::getCriterion, Function.identity()));

        byCriterion.values().forEach(this::validateCriterion);
        for (ComplexityCriterion criterion : ComplexityCriterion.values()) {
            CriterionConfigRequest request = byCriterion.get(criterion);
            ComplexityCriterionConfig config = existing.getOrDefault(criterion, new ComplexityCriterionConfig());
            config.setCriterion(criterion);
            config.setLowMin(request.lowMin());
            config.setLowMax(request.lowMax());
            config.setMediumMin(request.mediumMin());
            config.setMediumMax(request.mediumMax());
            config.setHighMin(request.highMin());
            config.setHighMax(request.highMax());
            config.setVeryHighMin(request.veryHighMin());
            config.setVeryHighMax(request.veryHighMax());
            config.setActive(request.active());
            criterionConfigs.save(config);
        }
        return getCriteria();
    }

    @Transactional(readOnly = true)
    public List<LevelConfigResponse> getLevels() {
        Map<DemandComplexity, ComplexityLevelConfig> byLevel = levelConfigs.findAll().stream()
                .collect(Collectors.toMap(ComplexityLevelConfig::getLevel, Function.identity()));
        return java.util.Arrays.stream(DemandComplexity.values())
                .filter(byLevel::containsKey)
                .map(byLevel::get)
                .map(LevelConfigResponse::from)
                .toList();
    }

    public List<LevelConfigResponse> updateLevels(List<LevelConfigRequest> requests) {
        Map<DemandComplexity, LevelConfigRequest> byLevel = requireAllLevels(requests);
        validateLevels(byLevel);

        for (DemandComplexity level : DemandComplexity.values()) {
            LevelConfigRequest request = byLevel.get(level);
            ComplexityLevelConfig config = levelConfigs.findById(level).orElseGet(ComplexityLevelConfig::new);
            config.setLevel(level);
            config.setMinScore(scale(request.minScore()));
            config.setMaxScore(scale(request.maxScore()));
            config.setEstimatedDurationMonths(request.estimatedDurationMonths());
            levelConfigs.save(config);
        }
        return getLevels();
    }

    public DemandComplexityResponse calculate(java.util.UUID demandId, String actor) {
        Demand demand = demands.findByIdAndDeletedAtIsNull(demandId)
                .orElseThrow(() -> new ResourceNotFoundException("Demanda não encontrada: " + demandId));

        Map<ComplexityCriterion, ComplexityCriterionConfig> configs = activeCriterionConfigs();
        int directionsCount = countDirections(demand);
        DemandProfileRequirementService.RequirementSummary requirements = profileRequirements.summary(demandId);
        int profilesCount = requirements.profilesCount();
        int totalResources = requirements.totalResources();
        int dependenciesCount = Math.toIntExact(dependencies.countByDemand_Id(demandId));

        int totalScore = score(configs.get(ComplexityCriterion.DIRECTIONS_COUNT), directionsCount)
                + score(configs.get(ComplexityCriterion.PROFILES_COUNT), profilesCount)
                + score(configs.get(ComplexityCriterion.TOTAL_RESOURCES), totalResources)
                + score(configs.get(ComplexityCriterion.DEPENDENCIES_COUNT), dependenciesCount);
        BigDecimal complexityScore = BigDecimal.valueOf(totalScore)
                .divide(BigDecimal.valueOf(ComplexityCriterion.values().length), 2, RoundingMode.HALF_UP);
        ComplexityLevelConfig level = levelFor(complexityScore);

        demand.setDirectionsCount(directionsCount);
        demand.setProfilesCount(profilesCount);
        demand.setTotalResources(totalResources);
        demand.setDependenciesCount(dependenciesCount);
        demand.setComplexityScore(complexityScore);
        demand.setComplexity(level.getLevel());
        demand.setEstimatedDurationMonths(level.getEstimatedDurationMonths());
        demand.setStatus("IN_ANALYSIS");
        demand.setPlannedStartDate(demand.getDesiredDate() == null ? null
                : demand.getDesiredDate().minusMonths(level.getEstimatedDurationMonths()));
        demand.setUpdatedBy(actor == null || actor.isBlank() ? "system" : actor.trim());
        Demand saved = demands.save(demand);

        return new DemandComplexityResponse(saved.getId(), saved.getDirectionsCount(), saved.getProfilesCount(),
                saved.getTotalResources(), saved.getDependenciesCount(), saved.getComplexityScore(),
                saved.getComplexity(), saved.getEstimatedDurationMonths(), saved.getDesiredDate(),
                saved.getPlannedStartDate());
    }

    private Map<ComplexityCriterion, CriterionConfigRequest> requireAllCriteria(List<CriterionConfigRequest> requests) {
        if (requests == null || requests.size() != ComplexityCriterion.values().length) {
            throw new BadRequestException("Devem ser informadas as quatro configurações de critérios");
        }
        Map<ComplexityCriterion, CriterionConfigRequest> result = new EnumMap<>(ComplexityCriterion.class);
        for (CriterionConfigRequest request : requests) {
            if (request == null || request.criterion() == null) {
                throw new BadRequestException("criterion é obrigatório");
            }
            if (result.put(request.criterion(), request) != null) {
                throw new BadRequestException("Critério duplicado: " + request.criterion());
            }
        }
        if (result.size() != ComplexityCriterion.values().length) {
            throw new BadRequestException("Todos os quatro critérios devem ser configurados");
        }
        return result;
    }

    private Map<DemandComplexity, LevelConfigRequest> requireAllLevels(List<LevelConfigRequest> requests) {
        if (requests == null || requests.size() != DemandComplexity.values().length) {
            throw new BadRequestException("Devem ser informadas as quatro configurações de níveis");
        }
        Map<DemandComplexity, LevelConfigRequest> result = new EnumMap<>(DemandComplexity.class);
        for (LevelConfigRequest request : requests) {
            if (request == null || request.level() == null) {
                throw new BadRequestException("level é obrigatório");
            }
            if (result.put(request.level(), request) != null) {
                throw new BadRequestException("Nível duplicado: " + request.level());
            }
        }
        if (result.size() != DemandComplexity.values().length) {
            throw new BadRequestException("Todos os quatro níveis devem ser configurados");
        }
        return result;
    }

    private void validateCriterion(CriterionConfigRequest request) {
        if (request.active() == null) {
            throw new BadRequestException("active é obrigatório para " + request.criterion());
        }
        requireRange("LOW", request.lowMin(), request.lowMax(), false);
        requireRange("MEDIUM", request.mediumMin(), request.mediumMax(), false);
        requireRange("HIGH", request.highMin(), request.highMax(), false);
        requireRange("VERY_HIGH", request.veryHighMin(), request.veryHighMax(), true);
        requireContinuous("LOW", request.lowMax(), "MEDIUM", request.mediumMin());
        requireContinuous("MEDIUM", request.mediumMax(), "HIGH", request.highMin());
        requireContinuous("HIGH", request.highMax(), "VERY_HIGH", request.veryHighMin());
    }

    private void requireRange(String level, Integer min, Integer max, boolean unboundedMaxAllowed) {
        if (min == null || (!unboundedMaxAllowed && max == null) || min < 0 || (max != null && max < 0)) {
            throw new BadRequestException("Os limites de " + level + " devem ser não negativos");
        }
        if (max != null && min > max) {
            throw new BadRequestException("Limite mínimo não pode ser maior que o máximo em " + level);
        }
    }

    private void requireContinuous(String currentLevel, int currentMax, String nextLevel, int nextMin) {
        int expected = currentMax + 1;
        if (nextMin < expected) {
            throw new BadRequestException("Os intervalos de " + currentLevel + " e " + nextLevel + " sobrepõem-se");
        }
        if (nextMin > expected) {
            throw new BadRequestException("Existe gap entre os intervalos de " + currentLevel + " e " + nextLevel);
        }
    }

    private void validateLevels(Map<DemandComplexity, LevelConfigRequest> requests) {
        for (DemandComplexity level : DemandComplexity.values()) {
            LevelConfigRequest request = requests.get(level);
            BigDecimal min = scale(request.minScore());
            BigDecimal max = scale(request.maxScore());
            if (min.compareTo(max) > 0) {
                throw new BadRequestException("minScore não pode ser maior que maxScore em " + level);
            }
            if (request.estimatedDurationMonths() == null || request.estimatedDurationMonths() <= 0) {
                throw new BadRequestException("estimatedDurationMonths deve ser maior que zero em " + level);
            }
        }
        if (scale(requests.get(DemandComplexity.LOW).minScore()).compareTo(MIN_SCORE) != 0
                || scale(requests.get(DemandComplexity.VERY_HIGH).maxScore()).compareTo(MAX_SCORE) != 0) {
            throw new BadRequestException("Os níveis devem cobrir scores de 1.00 a 4.00");
        }
        DemandComplexity[] levels = DemandComplexity.values();
        for (int i = 0; i < levels.length - 1; i++) {
            BigDecimal currentMax = scale(requests.get(levels[i]).maxScore());
            BigDecimal nextMin = scale(requests.get(levels[i + 1]).minScore());
            int comparison = nextMin.compareTo(currentMax.add(SCORE_STEP));
            if (comparison < 0) {
                throw new BadRequestException("Os intervalos de " + levels[i] + " e " + levels[i + 1]
                        + " sobrepõem-se");
            }
            if (comparison > 0) {
                throw new BadRequestException("Existe gap entre os intervalos de " + levels[i] + " e "
                        + levels[i + 1]);
            }
        }
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            throw new BadRequestException("Os limites de score são obrigatórios");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("Os limites de score devem ter no máximo duas casas decimais");
        }
    }

    private Map<ComplexityCriterion, ComplexityCriterionConfig> activeCriterionConfigs() {
        Map<ComplexityCriterion, ComplexityCriterionConfig> result = new EnumMap<>(ComplexityCriterion.class);
        criterionConfigs.findAll().stream().filter(ComplexityCriterionConfig::isActive)
                .forEach(config -> result.put(config.getCriterion(), config));
        if (result.size() != ComplexityCriterion.values().length) {
            throw new BadRequestException("Os quatro critérios de complexidade devem estar ativos e configurados");
        }
        return result;
    }

    private int countDirections(Demand demand) {
        Set<String> directions = new HashSet<>();
        addDirection(directions, demand.getDirectionCode(), demand.getDirectionName());
        participatingDirections.findByDemandIdOrderByCreatedAtAsc(demand.getId())
                .forEach(direction -> addDirection(directions, direction.getDirectionCode(), direction.getDirectionName()));
        return directions.size();
    }

    private void addDirection(Set<String> directions, String code, String name) {
        String value = code == null || code.isBlank() ? name : code;
        if (value != null && !value.isBlank()) {
            directions.add(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private int score(ComplexityCriterionConfig config, int value) {
        if (between(value, config.getLowMin(), config.getLowMax())) return 1;
        if (between(value, config.getMediumMin(), config.getMediumMax())) return 2;
        if (between(value, config.getHighMin(), config.getHighMax())) return 3;
        if (between(value, config.getVeryHighMin(), config.getVeryHighMax())) return 4;
        throw new BadRequestException("Valor " + value + " fora dos intervalos configurados para "
                + config.getCriterion());
    }

    private boolean between(int value, int min, Integer max) {
        return value >= min && (max == null || value <= max);
    }

    private ComplexityLevelConfig levelFor(BigDecimal score) {
        return levelConfigs.findAll().stream()
                .filter(config -> score.compareTo(config.getMinScore()) >= 0
                        && score.compareTo(config.getMaxScore()) <= 0)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Score sem nível de complexidade configurado: " + score));
    }
}
