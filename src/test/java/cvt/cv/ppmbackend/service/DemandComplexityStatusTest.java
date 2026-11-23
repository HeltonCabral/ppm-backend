package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.ComplexityCriterionConfig;
import cvt.cv.ppmbackend.entity.ComplexityLevelConfig;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.ComplexityCriterion;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.repository.ComplexityCriterionConfigRepository;
import cvt.cv.ppmbackend.repository.ComplexityLevelConfigRepository;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Calculating the complexity takes a draft into analysis, and leaves any later status alone. */
class DemandComplexityStatusTest {

    private final DemandRepository demands = mock(DemandRepository.class);
    private final ComplexityCriterionConfigRepository criteria = mock(ComplexityCriterionConfigRepository.class);
    private final ComplexityLevelConfigRepository levels = mock(ComplexityLevelConfigRepository.class);
    private final DemandParticipatingDirectionRepository participants = mock(DemandParticipatingDirectionRepository.class);
    private final DemandResourceDerivationService derivation = mock(DemandResourceDerivationService.class);
    private final DemandProfileRequirementService requirements = mock(DemandProfileRequirementService.class);
    private final DemandDependencyRepository dependencies = mock(DemandDependencyRepository.class);
    private final DemandPlanningCycleService cycles = mock(DemandPlanningCycleService.class);

    private final UUID demandId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final DemandPlanningCycle cycle = new DemandPlanningCycle();
    private DemandComplexityService service;

    @BeforeEach
    void setUp() {
        service = new DemandComplexityService(criteria, levels, demands, participants, requirements, derivation,
                dependencies, cycles);
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setDirectionCode("DSI");
        when(demands.findByIdAndDeletedAtIsNull(demandId)).thenReturn(Optional.of(demand));
        when(demands.save(any(Demand.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(cycles.resolveForMutation(demandId, planId)).thenReturn(cycle);
        when(participants.findByDemandIdOrderByCreatedAtAsc(demandId)).thenReturn(List.of());
        when(derivation.hasDerivedResources(any())).thenReturn(false);
        when(requirements.summary(demandId)).thenReturn(new DemandProfileRequirementService.RequirementSummary(0, 0));
        when(dependencies.countByDemand_Id(demandId)).thenReturn(0L);
        /* Every count falls in LOW, so the score is 1,00 and the level LOW. */
        when(criteria.findAll()).thenReturn(Arrays.stream(ComplexityCriterion.values()).map(this::criterion).toList());
        ComplexityLevelConfig low = new ComplexityLevelConfig();
        low.setLevel(DemandComplexity.LOW);
        low.setMinScore(new BigDecimal("1.00"));
        low.setMaxScore(new BigDecimal("5.00"));
        low.setEstimatedDurationMonths(3);
        when(levels.findAll()).thenReturn(List.of(low));
    }

    @Test
    void aDraftGoesIntoAnalysis() {
        cycle.setStatus(DemandPlanningStatus.DRAFT);

        service.calculate(demandId, planId, "u1");

        verify(cycles).transitionStatus(eq(cycle), eq(DemandPlanningStatus.IN_ANALYSIS), anyString(), eq("u1"));
    }

    @ParameterizedTest
    @EnumSource(value = DemandPlanningStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void pastDraftTheStatusStays(DemandPlanningStatus status) {
        cycle.setStatus(status);

        var result = service.calculate(demandId, planId, "u1");

        assertThat(result.complexity()).isEqualTo(DemandComplexity.LOW);
        verify(cycles, never()).transitionStatus(any(), any(), any(), any());
    }

    private ComplexityCriterionConfig criterion(ComplexityCriterion criterion) {
        ComplexityCriterionConfig config = new ComplexityCriterionConfig();
        config.setCriterion(criterion);
        config.setLowMin(0);
        config.setLowMax(10);
        config.setMediumMin(11);
        config.setMediumMax(20);
        config.setHighMin(21);
        config.setHighMax(30);
        config.setVeryHighMin(31);
        config.setVeryHighMax(null);
        config.setActive(true);
        return config;
    }
}
