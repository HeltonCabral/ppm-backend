package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.Create;
import cvt.cv.ppmbackend.dto.DemandDtos.DirectionAreaInput;
import cvt.cv.ppmbackend.dto.DemandDtos.DirectionAreaResponse;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandResponse;
import cvt.cv.ppmbackend.dto.DemandDtos.ParticipatingDirectionCreate;
import cvt.cv.ppmbackend.dto.DemandDtos.ParticipatingDirectionResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandArea;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirectionArea;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.DemandAreaRepository;
import cvt.cv.ppmbackend.repository.DemandCommitteeActionRepository;
import cvt.cv.ppmbackend.repository.DemandAttachmentRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandAreasTest {

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

    private DemandService service;

    private final UUID savedDemandId = UUID.randomUUID();
    private final UUID typeId = UUID.randomUUID();
    private PhaseModel type;
    private Demand savedDemand;
    private final List<DemandArea> savedRootAreas = new ArrayList<>();
    private final List<DemandParticipatingDirectionArea> savedPdAreas = new ArrayList<>();
    private final List<DemandParticipatingDirection> savedDirections = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DemandService(demands, planningCycles, attachments, history, participatingDirections,
                demandAreas, participatingDirectionAreas,
                codeService, historyService, committees, committeeSuggestions, projects, programs, operationalPlans,
                lookups, scoringService, scoreLifecycle, profileRequirements, successCriteria, supplierRepository,
                planningCycleService, dependencyService, phaseModels, committeeActions);

        type = new PhaseModel();
        type.setId(typeId);
        type.setCode("EVOLUTIVA");
        type.setName("Evolutiva");
        type.setActive(true);

        lenient().when(codeService.nextCode()).thenReturn("DEM-000001");
        lenient().when(phaseModels.findById(eq(typeId))).thenReturn(Optional.of(type));
        lenient().when(lookups.existsCode(anyString(), anyString())).thenReturn(false);

        lenient().when(demands.save(any(Demand.class))).thenAnswer(inv -> {
            Demand d = inv.getArgument(0);
            if (d.getId() == null) d.setId(savedDemandId);
            savedDemand = d;
            return d;
        });
        lenient().when(demands.findByIdAndDeletedAtIsNull(any(UUID.class))).thenAnswer(inv ->
                savedDemand != null && savedDemand.getId().equals(inv.getArgument(0))
                        ? Optional.of(savedDemand) : Optional.empty());

        lenient().when(planningCycleService.createInitialCycle(any(), any(), any(), any(), anyString()))
                .thenAnswer(inv -> {
                    DemandPlanningCycle c = new DemandPlanningCycle();
                    c.setId(UUID.randomUUID());
                    c.setStatus(DemandPlanningStatus.DRAFT);
                    c.setCycleEntryAt(Instant.now());
                    return c;
                });
        lenient().when(planningCycleService.getCurrentPlanningCycle(any(UUID.class))).thenReturn(Optional.empty());

        lenient().when(participatingDirections.save(any(DemandParticipatingDirection.class))).thenAnswer(inv -> {
            DemandParticipatingDirection pd = inv.getArgument(0);
            if (pd.getId() == null) pd.setId(UUID.randomUUID());
            savedDirections.add(pd);
            return pd;
        });
        lenient().when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> savedDirections);

        lenient().when(demandAreas.save(any(DemandArea.class))).thenAnswer(inv -> {
            DemandArea a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            savedRootAreas.add(a);
            return a;
        });
        lenient().when(demandAreas.findByDemandIdOrderByCreatedAtAsc(any(UUID.class))).thenAnswer(inv -> savedRootAreas);

        lenient().when(participatingDirectionAreas.save(any(DemandParticipatingDirectionArea.class))).thenAnswer(inv -> {
            DemandParticipatingDirectionArea a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            savedPdAreas.add(a);
            return a;
        });
        lenient().when(participatingDirectionAreas.findByParticipatingDirectionIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> savedPdAreas.stream()
                        .filter(a -> a.getParticipatingDirection().getId().equals(inv.getArgument(0)))
                        .toList());
    }

    @Test
    void persistsRootAreasAndParticipatingDirectionAreas() {
        Create req = baseCreate(
                List.of(
                        new DirectionAreaInput("GDS", "Gestão e Desenvolvimento", AreaRole.RESPONSIBLE),
                        new DirectionAreaInput("PIG", "Planeamento", AreaRole.INTERVENIENT)),
                List.of(new ParticipatingDirectionCreate("Direção Comercial", "DCE", "Planeamento de Vendas", "PPV",
                        DirectionParticipationType.INTERVENIENT, null,
                        List.of(
                                new DirectionAreaInput("PPV", "Planeamento de Vendas", AreaRole.RESPONSIBLE),
                                new DirectionAreaInput("CGC", "Controlo Gestão Comercial", AreaRole.INTERVENIENT)))));

        DemandResponse response = service.create(req, "helton");

        assertThat(response.areas())
                .extracting(DirectionAreaResponse::areaCode, DirectionAreaResponse::role)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("GDS", AreaRole.RESPONSIBLE),
                        org.assertj.core.api.Assertions.tuple("PIG", AreaRole.INTERVENIENT));

        assertThat(response.participatingDirections()).hasSize(1);
        ParticipatingDirectionResponse pd = response.participatingDirections().get(0);
        assertThat(pd.areas())
                .extracting(DirectionAreaResponse::areaCode, DirectionAreaResponse::role)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("PPV", AreaRole.RESPONSIBLE),
                        org.assertj.core.api.Assertions.tuple("CGC", AreaRole.INTERVENIENT));

        ArgumentCaptor<DemandArea> rootCaptor = ArgumentCaptor.forClass(DemandArea.class);
        verify(demandAreas, org.mockito.Mockito.times(2)).save(rootCaptor.capture());
        assertThat(rootCaptor.getAllValues()).extracting(DemandArea::getAreaCode).containsExactly("GDS", "PIG");

        ArgumentCaptor<DemandParticipatingDirectionArea> pdCaptor =
                ArgumentCaptor.forClass(DemandParticipatingDirectionArea.class);
        verify(participatingDirectionAreas, org.mockito.Mockito.times(2)).save(pdCaptor.capture());
        assertThat(pdCaptor.getAllValues()).extracting(DemandParticipatingDirectionArea::getAreaCode)
                .containsExactly("PPV", "CGC");
    }

    @Test
    void rejectsTwoResponsibleInSameDirection() {
        Create req = baseCreate(List.of(
                new DirectionAreaInput("GDS", "Gestão", AreaRole.RESPONSIBLE),
                new DirectionAreaInput("PIG", "Planeamento", AreaRole.RESPONSIBLE)), List.of());

        assertThatThrownBy(() -> service.create(req, "helton"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("RESPONSIBLE");
    }

    @Test
    void rejectsIntervenientWithoutResponsible() {
        Create req = baseCreate(List.of(
                new DirectionAreaInput("PIG", "Planeamento", AreaRole.INTERVENIENT)), List.of());

        assertThatThrownBy(() -> service.create(req, "helton"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("INTERVENIENT");
    }

    @Test
    void rejectsDuplicatedAreaInSameDirection() {
        Create req = baseCreate(List.of(
                new DirectionAreaInput("GDS", "Gestão", AreaRole.RESPONSIBLE),
                new DirectionAreaInput("GDS", "Gestão", AreaRole.INTERVENIENT)), List.of());

        assertThatThrownBy(() -> service.create(req, "helton"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("repetir");
    }

    @Test
    void rejectsResponsibleMismatchingRootAreaCode() {
        Create req = baseCreate(List.of(
                new DirectionAreaInput("XYZ", "Outra área", AreaRole.RESPONSIBLE)), List.of());

        assertThatThrownBy(() -> service.create(req, "helton"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("RESPONSIBLE");
    }

    @Test
    void createWithoutAreasKeepsBackwardCompatibility() {
        Create req = baseCreate(null, List.of());

        DemandResponse response = service.create(req, "helton");

        assertThat(response.areas()).isEmpty();
        verify(demandAreas, never()).save(any(DemandArea.class));
        verify(participatingDirectionAreas, never()).save(any(DemandParticipatingDirectionArea.class));
    }

    private Create baseCreate(List<DirectionAreaInput> areas,
            List<ParticipatingDirectionCreate> participatingDirections) {
        return new Create(
                "Demanda de teste", null, null, null,
                "Resumo", "requerente",
                "Gestão e Desenvolvimento", "GDS",
                "DSI", "DSI",
                DirectionParticipationType.IMPLEMANTATION, null, "sponsor",
                typeId, "MANUAL", null, null, null, null,
                null, null,
                "Problema", "Resultado esperado", null,
                "HIGH", null, null, null, null, null, null, null,
                null, participatingDirections, null, null,
                areas);
    }
}
