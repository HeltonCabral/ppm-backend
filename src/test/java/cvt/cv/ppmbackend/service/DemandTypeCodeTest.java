package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.Create;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The demand type may come as the phase model's code (Ex.: PROJ_TI) instead of its id. */
@ExtendWith(MockitoExtension.class)
class DemandTypeCodeTest {

    @Mock private DemandRepository demands;
    @Mock private DemandPlanningCycleRepository planningCycles;
    @Mock private DemandAttachmentRepository attachments;
    @Mock private DemandHistoryRepository history;
    @Mock private DemandParticipatingDirectionRepository participatingDirections;
    @Mock private DemandAreaRepository demandAreas;
    @Mock private DemandParticipatingDirectionAreaRepository participatingDirectionAreas;
    @Mock private DemandCodeService codeService;
    @Mock private DemandHistoryService historyService;
    @Mock private CommitteeService committees;
    @Mock private CommitteeSuggestionService committeeSuggestions;
    @Mock private ProjectService projects;
    @Mock private ProgramService programs;
    @Mock private OperationalPlanService operationalPlans;
    @Mock private LookupValueService lookups;
    @Mock private DemandScoringService scoringService;
    @Mock private DemandScoreLifecycleService scoreLifecycle;
    @Mock private DemandProfileRequirementService profileRequirements;
    @Mock private DemandSuccessCriterionService successCriteria;
    @Mock private SupplierRepository supplierRepository;
    @Mock private DemandPlanningCycleService planningCycleService;
    @Mock private DemandDependencyService dependencyService;
    @Mock private PhaseModelRepository phaseModels;
    @Mock private DemandCommitteeActionRepository committeeActions;
    @Mock private DemandCustomPlanningService customPlanning;
    @Mock private DemandAccessService access;

    private DemandService service;

    private final UUID projTiId = UUID.randomUUID();
    private final UUID projNegId = UUID.randomUUID();
    private Demand savedDemand;

    @BeforeEach
    void setUp() {
        service = new DemandService(demands, planningCycles, attachments, history, participatingDirections,
                demandAreas, participatingDirectionAreas,
                codeService, historyService, committees, committeeSuggestions, projects, programs, operationalPlans,
                lookups, scoringService, scoreLifecycle, profileRequirements, successCriteria, supplierRepository,
                planningCycleService, dependencyService, phaseModels, committeeActions, customPlanning, access);

        PhaseModel projTi = model(projTiId, "PROJ_TI", true);
        PhaseModel projNeg = model(projNegId, "PROJ_NEG", false);
        lenient().when(phaseModels.findById(eq(projTiId))).thenReturn(Optional.of(projTi));
        lenient().when(phaseModels.findById(eq(projNegId))).thenReturn(Optional.of(projNeg));
        lenient().when(phaseModels.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        lenient().when(phaseModels.findByCodeIgnoreCase(eq("proj_ti"))).thenReturn(Optional.of(projTi));
        lenient().when(phaseModels.findByCodeIgnoreCase(eq("PROJ_TI"))).thenReturn(Optional.of(projTi));
        lenient().when(phaseModels.findByCodeIgnoreCase(eq("PROJ_NEG"))).thenReturn(Optional.of(projNeg));

        lenient().when(codeService.nextCode()).thenReturn("DEM-000001");
        lenient().when(demands.save(any(Demand.class))).thenAnswer(inv -> {
            Demand d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            savedDemand = d;
            return d;
        });
        lenient().when(planningCycleService.createInitialCycle(any(), any(), any(), any(), anyString()))
                .thenAnswer(inv -> {
                    DemandPlanningCycle c = new DemandPlanningCycle();
                    c.setId(UUID.randomUUID());
                    c.setStatus(DemandPlanningStatus.DRAFT);
                    c.setCycleEntryAt(Instant.now());
                    return c;
                });
        lenient().when(planningCycleService.getCurrentPlanningCycle(any(UUID.class))).thenReturn(Optional.empty());
    }

    @Test
    void codeAloneSetsTheType() {
        service.create(create(null, "PROJ_TI"), "helton");

        assertThat(savedDemand.getType().getId()).isEqualTo(projTiId);
    }

    @Test
    void codeIsTrimmedBeforeTheLookup() {
        service.create(create(null, "  proj_ti "), "helton");

        assertThat(savedDemand.getType().getId()).isEqualTo(projTiId);
    }

    @Test
    void idAndCodeOfTheSameModelAreAccepted() {
        service.create(create(projTiId, "PROJ_TI"), "helton");

        assertThat(savedDemand.getType().getId()).isEqualTo(projTiId);
    }

    @Test
    void idStillWorksWithoutCode() {
        service.create(create(projTiId, null), "helton");

        assertThat(savedDemand.getType().getId()).isEqualTo(projTiId);
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> service.create(create(null, "PROJ_XX"), "helton"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode()).isEqualTo("PHASE_MODEL_CODE_NOT_FOUND"));
        verify(demands, never()).save(any(Demand.class));
    }

    @Test
    void idAndCodeOfDifferentModelsAreRejected() {
        assertThatThrownBy(() -> service.create(create(projNegId, "PROJ_TI"), "helton"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode()).isEqualTo("PHASE_MODEL_MISMATCH"));
        verify(demands, never()).save(any(Demand.class));
    }

    @Test
    void codeOfAnInactiveModelIsRejected() {
        assertThatThrownBy(() -> service.create(create(null, "PROJ_NEG"), "helton"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode()).isEqualTo("PHASE_MODEL_INACTIVE"));
        verify(demands, never()).save(any(Demand.class));
    }

    private static PhaseModel model(UUID id, String code, boolean active) {
        PhaseModel model = new PhaseModel();
        model.setId(id);
        model.setCode(code);
        model.setName(code);
        model.setActive(active);
        return model;
    }

    @Test
    void importCodeIsKeptTrimmed() {
        service.create(create(projTiId, null, " DMK-2027-001 "), "helton");

        assertThat(savedDemand.getImportCode()).isEqualTo("DMK-2027-001");
    }

    @Test
    void importCodeOfAnotherDemandIsRejected() {
        lenient().when(demands.existsByImportCodeIgnoreCaseAndDeletedAtIsNull("DMK-2027-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(create(projTiId, null, "DMK-2027-001"), "helton"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode()).isEqualTo("DEMAND_IMPORT_CODE_EXISTS"));
        verify(demands, never()).save(any(Demand.class));
    }

    private static Create create(UUID typeId, String typeCode) {
        return create(typeId, typeCode, null);
    }

    private static Create create(UUID typeId, String typeCode, String importCode) {
        return new Create(
                "Demanda importada", null, null, null,
                "Resumo", "requerente",
                null, null,
                "DSI", "DSI",
                DirectionParticipationType.IMPLEMANTATION, null, "sponsor",
                typeId, typeCode, importCode, "MANUAL", null, null, null, null,
                null, null,
                "Problema", "Resultado esperado", null,
                "HIGH", null, null, null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
