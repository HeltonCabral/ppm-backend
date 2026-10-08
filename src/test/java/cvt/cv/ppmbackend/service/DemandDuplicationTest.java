package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.Duplicate;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementInput;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementResponse;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandSuccessCriterionResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandAttachment;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.StrategicPillar;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.repository.DemandAttachmentRepository;
import cvt.cv.ppmbackend.repository.DemandAreaRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandDuplicationTest {

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

    private final UUID sourceId = UUID.randomUUID();
    private final UUID copyId = UUID.randomUUID();
    private final UUID pillarId = UUID.randomUUID();
    private final UUID activeProfileId = UUID.randomUUID();
    private final UUID inactiveProfileId = UUID.randomUUID();

    private Demand source;
    private Demand savedCopy;

    @BeforeEach
    void setUp() {
        service = new DemandService(demands, planningCycles, attachments, history, participatingDirections,
                demandAreas, participatingDirectionAreas,
                codeService, historyService, committees, committeeSuggestions, projects, programs, operationalPlans,
                lookups, scoringService, scoreLifecycle, profileRequirements, successCriteria, supplierRepository,
                planningCycleService, dependencyService, phaseModels, committeeActions, customPlanning, access);

        source = sourceDemand();

        lenient().when(demands.findByIdAndDeletedAtIsNull(any(UUID.class))).thenAnswer(invocation -> {
            UUID requested = invocation.getArgument(0);
            return sourceId.equals(requested) ? Optional.of(source) : Optional.ofNullable(savedCopy);
        });
        lenient().when(demands.save(any(Demand.class))).thenAnswer(invocation -> {
            Demand demand = invocation.getArgument(0);
            if (demand.getId() == null) {
                demand.setId(copyId);
            }
            savedCopy = demand;
            return demand;
        });
        lenient().when(attachments.save(any(DemandAttachment.class))).thenAnswer(invocation -> {
            DemandAttachment attachment = invocation.getArgument(0);
            if (attachment.getId() == null) {
                attachment.setId(UUID.randomUUID());
            }
            return attachment;
        });
        lenient().when(codeService.nextCode()).thenReturn("DEM-000002");
        lenient().when(phaseModels.findById(eq(source.getTypeId())))
                .thenReturn(Optional.of(source.getType()));
        lenient().when(lookups.existsCode(anyString(), anyString())).thenReturn(false);
        lenient().when(planningCycleService.createInitialCycle(any(Demand.class), any(), any(), any(), anyString()))
                .thenReturn(cycleWithPillar());
        lenient().when(planningCycleService.getCurrentPlanningCycle(any(UUID.class)))
                .thenAnswer(invocation -> sourceId.equals(invocation.getArgument(0))
                        ? Optional.of(cycleWithPillar())
                        : Optional.empty());

        lenient().when(attachments.findByDemandIdOrderByCreatedAtDesc(any(UUID.class)))
                .thenAnswer(invocation -> sourceId.equals(invocation.getArgument(0))
                        ? List.of(attachment("Estudo previo.pdf", Instant.parse("2026-01-02T00:00:00Z")),
                                attachment("Ata de reuniao.pdf", Instant.parse("2026-01-01T00:00:00Z")))
                        : List.of());
        lenient().when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(invocation -> sourceId.equals(invocation.getArgument(0))
                        ? List.of(participatingDirection())
                        : List.of());
        lenient().when(profileRequirements.list(any(UUID.class)))
                .thenAnswer(invocation -> sourceId.equals(invocation.getArgument(0))
                        ? List.of(profileRequirement(activeProfileId, true, 3, 50),
                                profileRequirement(inactiveProfileId, false, 1, 25))
                        : List.of());
        lenient().when(successCriteria.list(any(UUID.class)))
                .thenAnswer(invocation -> sourceId.equals(invocation.getArgument(0))
                        ? List.of(new DemandSuccessCriterionResponse(UUID.randomUUID(), "Reduzir SLA em 30%", 0))
                        : List.of());
        lenient().when(dependencyService.listByDemand(any(UUID.class))).thenReturn(List.of());
    }

    @Test
    void copiaOsDadosDaDemandaDeOrigemComCodigoENovoCicloProprios() {
        service.duplicate(sourceId, null, "helton");

        assertThat(savedCopy).isNotNull();
        assertThat(savedCopy.getCode()).isEqualTo("DEM-000002");
        assertThat(savedCopy.getTitle()).isEqualTo("Cópia de Modernizar o portal do cliente");
        assertThat(savedCopy.getProblemStatement()).isEqualTo(source.getProblemStatement());
        assertThat(savedCopy.getDesiredOutcome()).isEqualTo(source.getDesiredOutcome());
        assertThat(savedCopy.getDirectionCode()).isEqualTo("DSI");
        assertThat(savedCopy.getAreaCode()).isEqualTo("GDS");
        assertThat(savedCopy.getNature()).isEqualTo(DemandNature.REGULATORY);
        assertThat(savedCopy.getEstimatedBudget()).isEqualByComparingTo("125000.00");
        assertThat(savedCopy.getCreatedBy()).isEqualTo("helton");
        assertThat(savedCopy.getDependenciesCount()).isZero();

        verify(planningCycleService).createInitialCycle(eq(savedCopy), eq(pillarId), eq(null), eq(null), eq("helton"));
        verify(historyService).log(eq(savedCopy), eq("DUPLICATED"), eq(null), eq(DemandPlanningStatus.DRAFT.name()),
                anyString(), eq("helton"), eq("helton"), any());
    }

    @Test
    void copiaColecoesIgnorandoPerfisInativos() {
        service.duplicate(sourceId, null, "helton");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DemandProfileRequirementInput>> profileCaptor = ArgumentCaptor.forClass(List.class);
        verify(profileRequirements).sync(eq(savedCopy), profileCaptor.capture());
        assertThat(profileCaptor.getValue()).hasSize(1);
        assertThat(profileCaptor.getValue().get(0).profileId()).isEqualTo(activeProfileId);
        assertThat(profileCaptor.getValue().get(0).requiredQuantity()).isEqualTo(3);
        assertThat(profileCaptor.getValue().get(0).phaseIds()).hasSize(1);

        ArgumentCaptor<DemandParticipatingDirection> directionCaptor =
                ArgumentCaptor.forClass(DemandParticipatingDirection.class);
        verify(participatingDirections).save(directionCaptor.capture());
        assertThat(directionCaptor.getValue().getDirectionCode()).isEqualTo("DFI");
        assertThat(directionCaptor.getValue().getParticipationType())
                .isEqualTo(DirectionParticipationType.INTERVENIENT);

        ArgumentCaptor<DemandAttachment> attachmentCaptor = ArgumentCaptor.forClass(DemandAttachment.class);
        verify(attachments, org.mockito.Mockito.times(2)).save(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getAllValues()).extracting(DemandAttachment::getName)
                .containsExactly("Ata de reuniao.pdf", "Estudo previo.pdf");
    }

    @Test
    void respeitaTituloPersonalizadoEFlagsDesligadas() {
        Duplicate options = new Duplicate("Fase 2 do portal do cliente", false, false, false, false, false);

        service.duplicate(sourceId, options, "helton");

        assertThat(savedCopy.getTitle()).isEqualTo("Fase 2 do portal do cliente");
        verify(attachments, never()).save(any(DemandAttachment.class));
        verify(participatingDirections, never()).save(any(DemandParticipatingDirection.class));
        verify(profileRequirements).sync(eq(savedCopy), eq(null));
        verify(successCriteria).sync(eq(savedCopy), eq(null));
        verify(planningCycleService).createInitialCycle(eq(savedCopy), eq(null), eq(null), eq(null), eq("helton"));
    }

    private Demand sourceDemand() {
        PhaseModel type = new PhaseModel();
        type.setId(UUID.randomUUID());
        type.setCode("EVOLUTIVA");
        type.setName("Evolutiva");
        type.setActive(true);

        Demand demand = new Demand();
        demand.setId(sourceId);
        demand.setCode("DEM-000001");
        demand.setTitle("Modernizar o portal do cliente");
        demand.setSummary("Renovação do portal de autoatendimento");
        demand.setRequester("Área de Clientes");
        demand.setAreaName("Gestão e Desenvolvimento de Sistemas");
        demand.setAreaCode("GDS");
        demand.setDirectionName("Direção de Sistemas de Informação");
        demand.setDirectionCode("DSI");
        demand.setDirectionParticipationType(DirectionParticipationType.IMPLEMANTATION);
        demand.setNature(DemandNature.REGULATORY);
        demand.setSponsor("Administração");
        demand.setType(type);
        demand.setOrigin("MANUAL");
        demand.setImpactedSystem("Portal do Cliente");
        demand.setProblemStatement("O portal atual não suporta autenticação forte.");
        demand.setDesiredOutcome("Portal com autenticação forte e nova jornada de adesão.");
        demand.setProposedResponse("Reescrita incremental do portal.");
        demand.setUrgency("HIGH");
        demand.setEstimatedBudget(new BigDecimal("125000.00"));
        demand.setDesiredDate(LocalDate.of(2027, 3, 31));
        demand.setNotes("Notas da demanda original");
        demand.setDependenciesCount(4);
        demand.setComplexityScore(new BigDecimal("3.50"));
        return demand;
    }

    private DemandPlanningCycle cycleWithPillar() {
        StrategicPillar pillar = new StrategicPillar();
        pillar.setId(pillarId);

        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setDemand(source);
        cycle.setStatus(DemandPlanningStatus.DRAFT);
        cycle.setStrategicPillar(pillar);
        cycle.setCycleEntryAt(Instant.now());
        return cycle;
    }

    private DemandAttachment attachment(String name, Instant createdAt) {
        DemandAttachment attachment = new DemandAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setDemand(source);
        attachment.setName(name);
        attachment.setUrl("https://arquivo.local/" + name);
        attachment.setContentType("application/pdf");
        attachment.setCreatedAt(createdAt);
        return attachment;
    }

    private DemandParticipatingDirection participatingDirection() {
        DemandParticipatingDirection direction = new DemandParticipatingDirection();
        direction.setId(UUID.randomUUID());
        direction.setDemand(source);
        direction.setDirectionName("Direção Financeira");
        direction.setDirectionCode("DFI");
        direction.setAreaName("Controlo de Gestão");
        direction.setAreaCode("PCG");
        direction.setParticipationType(DirectionParticipationType.INTERVENIENT);
        direction.setObservations("Valida o impacto orçamental");
        return direction;
    }

    private DemandProfileRequirementResponse profileRequirement(UUID profileId, boolean active, int quantity,
            int allocation) {
        // 'allocation' é ignorado: o modelo atual não usa alocação percentual.
        return new DemandProfileRequirementResponse(UUID.randomUUID(), profileId, "Perfil " + profileId, "DSI",
                10, active, quantity, List.of(new cvt.cv.ppmbackend.dto.DemandDtos.DemandPhaseRef(
                        UUID.randomUUID(), "DEV", "Desenvolvimento", 1)));
    }
}
