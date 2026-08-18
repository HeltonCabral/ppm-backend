package cvt.cv.ppmbackend.config;

import cvt.cv.ppmbackend.entity.ComplexityCriterionConfig;
import cvt.cv.ppmbackend.entity.ComplexityLevelConfig;
import cvt.cv.ppmbackend.enums.ComplexityCriterion;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.repository.ComplexityCriterionConfigRepository;
import cvt.cv.ppmbackend.repository.ComplexityLevelConfigRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class ComplexityConfigurationInitializer implements ApplicationRunner {
    private final ComplexityCriterionConfigRepository criteria;
    private final ComplexityLevelConfigRepository levels;

    public ComplexityConfigurationInitializer(ComplexityCriterionConfigRepository criteria,
            ComplexityLevelConfigRepository levels) {
        this.criteria = criteria;
        this.levels = levels;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        criterion(ComplexityCriterion.DIRECTIONS_COUNT, 1, 1, 2, 2, 3, 4, 5, null);
        criterion(ComplexityCriterion.PROFILES_COUNT, 1, 2, 3, 4, 5, 6, 7, null);
        criterion(ComplexityCriterion.TOTAL_RESOURCES, 1, 2, 3, 5, 6, 10, 11, null);
        criterion(ComplexityCriterion.DEPENDENCIES_COUNT, 0, 0, 1, 1, 2, 3, 4, null);

        level(DemandComplexity.LOW, "1.00", "1.49", 1);
        level(DemandComplexity.MEDIUM, "1.50", "2.49", 3);
        level(DemandComplexity.HIGH, "2.50", "3.49", 6);
        level(DemandComplexity.VERY_HIGH, "3.50", "4.00", 12);
    }

    private void criterion(ComplexityCriterion criterion, int lowMin, int lowMax, int mediumMin, int mediumMax,
            int highMin, int highMax, int veryHighMin, Integer veryHighMax) {
        if (criteria.findByCriterion(criterion).isPresent()) {
            return;
        }
        ComplexityCriterionConfig config = new ComplexityCriterionConfig();
        config.setCriterion(criterion);
        config.setLowMin(lowMin);
        config.setLowMax(lowMax);
        config.setMediumMin(mediumMin);
        config.setMediumMax(mediumMax);
        config.setHighMin(highMin);
        config.setHighMax(highMax);
        config.setVeryHighMin(veryHighMin);
        config.setVeryHighMax(veryHighMax);
        config.setActive(true);
        criteria.save(config);
    }

    private void level(DemandComplexity level, String minScore, String maxScore, int duration) {
        if (levels.existsById(level)) {
            return;
        }
        ComplexityLevelConfig config = new ComplexityLevelConfig();
        config.setLevel(level);
        config.setMinScore(new BigDecimal(minScore));
        config.setMaxScore(new BigDecimal(maxScore));
        config.setEstimatedDurationMonths(duration);
        levels.save(config);
    }
}
