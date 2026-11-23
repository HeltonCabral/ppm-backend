package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandAreaRepository;
import cvt.cv.ppmbackend.repository.DemandAttachmentRepository;
import cvt.cv.ppmbackend.repository.DemandCommitteeActionRepository;
import cvt.cv.ppmbackend.repository.DemandHistoryRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionAreaRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import cvt.cv.ppmbackend.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Only a demand that is a draft in every planning cycle can be deleted. */
class DemandDeleteDraftTest {

    private final DemandRepository demands = mock(DemandRepository.class);
    private final DemandPlanningCycleRepository planningCycles = mock(DemandPlanningCycleRepository.class);
    private final DemandPlanningCycleService planningCycleService = mock(DemandPlanningCycleService.class);
    private final DemandHistoryService historyService = mock(DemandHistoryService.class);
    private final UUID id = UUID.randomUUID();
    private final Demand demand = new Demand();

    private DemandService service;

    @BeforeEach
    void setUp() {
        service = new DemandService(demands, planningCycles, mock(DemandAttachmentRepository.class),
                mock(DemandHistoryRepository.class), mock(DemandParticipatingDirectionRepository.class),
                mock(DemandAreaRepository.class), mock(DemandParticipatingDirectionAreaRepository.class),
                mock(DemandCodeService.class), historyService, mock(CommitteeService.class),
                mock(CommitteeSuggestionService.class), mock(ProjectService.class), mock(ProgramService.class),
                mock(OperationalPlanService.class), mock(LookupValueService.class), mock(DemandScoringService.class),
                mock(DemandScoreLifecycleService.class), mock(DemandProfileRequirementService.class),
                mock(DemandSuccessCriterionService.class), mock(SupplierRepository.class),
                planningCycleService, mock(DemandDependencyService.class), mock(PhaseModelRepository.class),
                mock(DemandCommitteeActionRepository.class), mock(DemandCustomPlanningService.class),
                mock(DemandAccessService.class));
        demand.setId(id);
        when(demands.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(demand));
    }

    @Test
    void draftIsDeleted() {
        DemandPlanningCycle draft = cycle(DemandPlanningStatus.DRAFT);
        when(planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(id)).thenReturn(List.of(draft));

        service.delete(id, "u1");

        assertThat(demand.getDeletedAt()).isNotNull();
        verify(planningCycleService).transitionStatus(eq(draft), eq(DemandPlanningStatus.BACKLOG), anyString(), eq("u1"));
    }

    @Test
    void demandPastDraftIsRefused() {
        when(planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(id))
                .thenReturn(List.of(cycle(DemandPlanningStatus.IN_ANALYSIS)));

        assertThatThrownBy(() -> service.delete(id, "u1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Draft");
        assertThat(demand.getDeletedAt()).isNull();
        verify(planningCycleService, never()).transitionStatus(any(), any(), any(), any());
    }

    @Test
    void draftInOnePlanButNotInAnotherIsRefused() {
        when(planningCycles.findAllByDemand_IdOrderByCycleEntryAtDesc(id))
                .thenReturn(List.of(cycle(DemandPlanningStatus.DRAFT), cycle(DemandPlanningStatus.IN_PLAN)));

        assertThatThrownBy(() -> service.delete(id, "u1")).isInstanceOf(DomainException.class);
        assertThat(demand.getDeletedAt()).isNull();
    }

    private DemandPlanningCycle cycle(DemandPlanningStatus status) {
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setStatus(status);
        return cycle;
    }
}
