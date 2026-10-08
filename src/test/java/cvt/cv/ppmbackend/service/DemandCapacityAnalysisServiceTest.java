package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandCapacityAnalysisDtos.CapacityAnalysisResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.CapacityStatus;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandCapacityAnalysisServiceTest {

    @Mock
    private DemandPlanningCycleRepository planningCycles;
    @Mock
    private DemandPlanningCycleService planningCycleService;
    @Mock
    private DemandProfileRequirementRepository profileRequirements;
    @Mock
    private DemandResourceDerivationService resourceDerivation;
    @Mock
    private CapacityCalculator calculator;

    private DemandCapacityAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new DemandCapacityAnalysisService(planningCycles, planningCycleService, profileRequirements,
                resourceDerivation, calculator);
    }

    @Test
    void returnsNotCalculatedWhenDemandHasNoPlannedStartDate() {
        UUID demandId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setDesiredDate(LocalDate.of(2027, 6, 30));
        demand.setPlannedStartDate(null);
        when(planningCycleService.resolveForMutation(demandId, planId)).thenReturn(cycle(demand));

        CapacityAnalysisResponse response = service.analyze(demandId, planId);

        assertThat(response.status()).isEqualTo(CapacityStatus.NOT_CALCULATED);
        assertThat(response.reason()).isEqualTo("Sem início previsto/duração: calcule a complexidade da demanda.");
        assertThat(response.profiles()).isEmpty();
        assertThat(response.peakUtilization()).isNull();
        assertThat(response.criticalPeriod()).isNull();
    }

    @Test
    void returnsOkWithoutReasonWhenScheduledButNoRequirements() {
        UUID demandId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPlannedStartDate(LocalDate.of(2027, 1, 1));
        demand.setDesiredDate(LocalDate.of(2027, 6, 30));
        when(planningCycleService.resolveForMutation(demandId, planId)).thenReturn(cycle(demand));
        when(resourceDerivation.hasDerivedResources(demand)).thenReturn(false);
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(demandId)).thenReturn(List.of());

        CapacityAnalysisResponse response = service.analyze(demandId, planId);

        assertThat(response.status()).isEqualTo(CapacityStatus.OK);
        assertThat(response.reason()).isNull();
        assertThat(response.profiles()).isEmpty();
    }

    private DemandPlanningCycle cycle(Demand demand) {
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setDemand(demand);
        return cycle;
    }
}
