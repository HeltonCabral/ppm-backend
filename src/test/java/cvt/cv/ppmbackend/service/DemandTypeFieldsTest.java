package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.dto.DemandDtos.Create;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandResponse;
import cvt.cv.ppmbackend.dto.DemandDtos.Update;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DemandTypeFieldsTest {

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

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private DemandService service;

    private final UUID formacaoId = UUID.randomUUID();
    private final UUID evolutivaId = UUID.randomUUID();
    private Demand savedDemand;

    @BeforeEach
    void setUp() {
        service = new DemandService(demands, planningCycles, attachments, history, participatingDirections,
                demandAreas, participatingDirectionAreas,
                codeService, historyService, committees, committeeSuggestions, projects, programs, operationalPlans,
                lookups, scoringService, scoreLifecycle, profileRequirements, successCriteria, supplierRepository,
                planningCycleService, dependencyService, phaseModels, committeeActions, customPlanning, access);

        PhaseModel formacao = TypeFieldRulesTest.formacao();
        formacao.setId(formacaoId);
        formacao.setActive(true);
        PhaseModel evolutiva = new PhaseModel();
        evolutiva.setId(evolutivaId);
        evolutiva.setName("Evolutiva");
        evolutiva.setActive(true);

        lenient().when(codeService.nextCode()).thenReturn("DEM-000001");
        lenient().when(phaseModels.findById(formacaoId)).thenReturn(Optional.of(formacao));
        lenient().when(phaseModels.findById(evolutivaId)).thenReturn(Optional.of(evolutiva));
        lenient().when(lookups.existsCode(anyString(), anyString())).thenReturn(false);
        lenient().when(demands.save(any(Demand.class))).thenAnswer(inv -> {
            Demand d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            savedDemand = d;
            return d;
        });
        lenient().when(demands.findByIdAndDeletedAtIsNull(any(UUID.class))).thenAnswer(inv -> Optional.of(savedDemand));
        lenient().when(planningCycleService.createInitialCycle(any(), any(), any(), any(), anyString()))
                .thenAnswer(inv -> {
                    DemandPlanningCycle c = new DemandPlanningCycle();
                    c.setStatus(DemandPlanningStatus.DRAFT);
                    c.setCycleEntryAt(Instant.now());
                    return c;
                });
        lenient().when(planningCycleService.getCurrentPlanningCycle(any(UUID.class))).thenReturn(Optional.empty());
    }

    // 4. typeFields válidos: gravados e devolvidos tal como enviados; 6. chave desconhecida ignorada.
    @Test
    void createStoresValidTypeFieldsAndIgnoresUnknownKeys() throws Exception {
        DemandResponse response = service.create(create(formacaoId,
                "{\"modalidade\":\"HIBRIDA\",\"local\":[\"SANTIAGO\"],\"participantes\":25,\"x\":1}"), "helton");

        assertThat(response.typeFields()).isEqualTo(
                Map.of("modalidade", "HIBRIDA", "local", List.of("SANTIAGO"), "participantes", 25));
        assertThat(savedDemand.getTypeFields()).containsOnlyKeys("modalidade", "local", "participantes");
    }

    @Test
    void createWithoutTypeFieldsReturnsEmptyObject() throws Exception {
        assertThat(service.create(create(formacaoId, null), "helton").typeFields()).isEmpty();
    }

    // 5. participantes: 0 com min: 1 → 400 com o label na mensagem.
    @Test
    void createRejectsValueBelowMinimum() {
        assertThatThrownBy(() -> service.create(create(formacaoId, "{\"participantes\":0}"), "helton"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("N.º de participantes: mínimo 1.");
    }

    @Test
    void updateWithoutTypeFieldsKeepsThemAndWithTypeFieldsReplacesThem() throws Exception {
        service.create(create(formacaoId, "{\"modalidade\":\"ONLINE\",\"participantes\":5}"), "helton");

        service.update(savedDemand.getId(), update(formacaoId, null), "helton");
        assertThat(savedDemand.getTypeFields()).containsOnlyKeys("modalidade", "participantes");

        service.update(savedDemand.getId(), update(formacaoId, "{\"participantes\":8}"), "helton");
        assertThat(savedDemand.getTypeFields()).isEqualTo(Map.of("participantes", 8));
    }

    // 8. Mudar o Tipo sem enviar typeFields → typeFields fica {}.
    @Test
    void changingTypeWithoutTypeFieldsClearsThem() throws Exception {
        service.create(create(formacaoId, "{\"modalidade\":\"ONLINE\"}"), "helton");

        DemandResponse response = service.update(savedDemand.getId(), update(evolutivaId, null), "helton");

        assertThat(response.typeFields()).isEmpty();
    }

    private Create create(UUID typeId, String typeFields) throws Exception {
        return mapper.readValue(body(typeId, typeFields), Create.class);
    }

    private Update update(UUID typeId, String typeFields) throws Exception {
        return mapper.readValue(body(typeId, typeFields), Update.class);
    }

    private static String body(UUID typeId, String typeFields) {
        return "{\"title\":\"Formação PMO\",\"problemStatement\":\"Problema\",\"desiredOutcome\":\"Resultado\","
                + "\"typeId\":\"" + typeId + "\"" + (typeFields == null ? "" : ",\"typeFields\":" + typeFields) + "}";
    }
}
