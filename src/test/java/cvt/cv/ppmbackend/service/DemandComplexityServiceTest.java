package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.CriterionConfigRequest;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.LevelConfigRequest;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementInput;
import cvt.cv.ppmbackend.dto.ProfileDtos.CreateRequest;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.ComplexityCriterion;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.ProfileCategory;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DemandComplexityServiceTest {
    @Autowired DemandComplexityService complexity;
    @Autowired DemandRepository demands;
    @Autowired DemandParticipatingDirectionRepository participatingDirections;
    @Autowired LookupValueRepository lookups;
    @Autowired DemandProfileRequirementService profileRequirements;
    @Autowired ProfileService profiles;
    @Autowired DemandDependencyService dependencies;

    @Test
    void calculatesAndPersistsComplexityWithoutChangingScoringOrCapacity() {
        Demand demand = demand();
        Demand target = new Demand();
        target.setCode("DEM-COMPLEXITY-DEP");
        target.setTitle("Dependência do teste de complexidade");
        target.setType(demand.getType());
        target = demands.save(target);
        dependencies.create(demand.getId(), new cvt.cv.ppmbackend.dto.DemandDependencyDtos.CreateRequest(
                target.getId(), DemandDependencyType.BLOCKING, null), "analyst");
        DemandParticipatingDirection direction = new DemandParticipatingDirection();
        direction.setDemand(demand);
        direction.setDirectionCode("D2");
        direction.setDirectionName("Direção 2");
        direction.setParticipationType(DirectionParticipationType.INTERVENIENT);
        participatingDirections.save(direction);
        var analyst = profiles.create(new CreateRequest("Analista", ProfileCategory.TECHNOLOGY, null, 5));
        var architect = profiles.create(new CreateRequest("Arquiteto", ProfileCategory.TECHNOLOGY, null, 3));
        var manager = profiles.create(new CreateRequest("Gestor", ProfileCategory.MANAGEMENT, null, 2));
        profileRequirements.sync(demand, List.of(
                new DemandProfileRequirementInput(analyst.id(), 2, 100),
                new DemandProfileRequirementInput(architect.id(), 3, 50),
                new DemandProfileRequirementInput(manager.id(), 1, 25)));

        var result = complexity.calculate(demand.getId(), "analyst");

        assertThat(result.directionsCount()).isEqualTo(2);
        assertThat(result.profilesCount()).isEqualTo(3);
        assertThat(result.totalResources()).isEqualTo(6);
        assertThat(result.dependenciesCount()).isEqualTo(1);
        assertThat(result.complexityScore()).isEqualByComparingTo("2.25");
        assertThat(result.complexity()).isEqualTo(DemandComplexity.MEDIUM);
        assertThat(result.estimatedDurationMonths()).isEqualTo(3);
        assertThat(result.plannedStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));

        Demand saved = demands.findById(demand.getId()).orElseThrow();
        assertThat(saved.getScoreTotal()).isEqualByComparingTo("9.50");
        assertThat(saved.getCapacityStatus()).isEqualTo("AVAILABLE");
        assertThat(saved.getUpdatedBy()).isEqualTo("analyst");
    }

    @Test
    void rejectsCriterionGapsAndLevelDurationsThatAreNotPositive() {
        List<CriterionConfigRequest> criteria = complexity.getCriteria().stream()
                .map(config -> config.criterion() == ComplexityCriterion.DIRECTIONS_COUNT
                        ? new CriterionConfigRequest(config.criterion(), 1, 1, 3, 3, 4, 4, 5, null, true)
                        : new CriterionConfigRequest(config.criterion(), config.lowMin(), config.lowMax(),
                                config.mediumMin(), config.mediumMax(), config.highMin(), config.highMax(),
                                config.veryHighMin(), config.veryHighMax(), config.active()))
                .toList();

        assertThatThrownBy(() -> complexity.updateCriteria(criteria))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gap");

        List<LevelConfigRequest> levels = complexity.getLevels().stream()
                .map(config -> new LevelConfigRequest(config.level(), config.minScore(), config.maxScore(),
                        config.level() == DemandComplexity.HIGH ? 0 : config.estimatedDurationMonths()))
                .toList();

        assertThatThrownBy(() -> complexity.updateLevels(levels))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("estimatedDurationMonths");
    }

    private Demand demand() {
        LookupValue type = new LookupValue();
        type.setCategory("DEMAND_TYPE");
        type.setCode("COMPLEXITY_TEST");
        type.setLabel("Teste de complexidade");
        type = lookups.save(type);

        Demand demand = new Demand();
        demand.setCode("DEM-COMPLEXITY-TEST");
        demand.setTitle("Demanda para teste de complexidade");
        demand.setType(type);
        demand.setDirectionCode("D1");
        demand.setDirectionName("Direção 1");
        demand.setDesiredDate(LocalDate.of(2026, 12, 1));
        demand.setScoreTotal(new BigDecimal("9.50"));
        demand.setCapacityStatus("AVAILABLE");
        return demands.save(demand);
    }
}
