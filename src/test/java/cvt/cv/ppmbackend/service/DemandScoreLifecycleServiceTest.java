package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cvt.cv.ppmbackend.dto.DemandDtos.ConvertToProject;
import cvt.cv.ppmbackend.dto.DemandDtos.StatusPatch;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.ScoreInput;
import cvt.cv.ppmbackend.dto.DemandScoringDtos.UpsertRequest;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ScoringCriterion;
import cvt.cv.ppmbackend.enums.Priority;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandAttachmentRepository;
import cvt.cv.ppmbackend.repository.DemandHistoryRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.DemandScoringRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemandScoreLifecycleServiceTest {
    private DemandScoreLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        lifecycle = new DemandScoreLifecycleService(objectMapper);
    }

    @Test
    void invalidationPersistsSnapshotAndRemovesCurrentScore() {
        Demand demand = scoredDemand("READY_FOR_COMMITTEE");

        lifecycle.applyStatusTransition(demand, "READY_FOR_COMMITTEE", "IN_ANALYSIS", null);

        assertThat(demand.getScoreStatus()).isEqualTo("Desatualizado");
        assertThat(demand.getScoreTotal()).isNull();
        assertThat(demand.getPortfolioRank()).isNull();
        assertThat(demand.getDirectionRank()).isNull();
        assertThat(demand.getScoreInvalidatedAt()).isNotNull();
        assertThat(demand.getScoreInvalidationReason()).isNotBlank();
        assertThat(lifecycle.previousScoreSnapshot(demand).get("scoreTotal").decimalValue())
                .isEqualByComparingTo("82.50");
        assertThat(lifecycle.previousScoreSnapshot(demand).get("portfolioRank").intValue()).isEqualTo(4);
        assertThat(lifecycle.previousScoreSnapshot(demand).get("directionRank").intValue()).isEqualTo(2);
    }

    @Test
    void recalculationRestoresValidStateAndClearsInvalidation() {
        Demand demand = scoredDemand("IN_PRIORITIZATION");
        lifecycle.applyStatusTransition(demand, "IN_PRIORITIZATION", "REJECTED", "Fora de âmbito");
        demand.setScoreTotal(new BigDecimal("91.00"));
        demand.setPortfolioRank(1);

        lifecycle.markCalculated(demand);

        assertThat(demand.getScoreStatus()).isEqualTo("Válido");
        assertThat(demand.getScoreCalculatedAt()).isNotNull();
        assertThat(demand.getScoreInvalidatedAt()).isNull();
        assertThat(demand.getScoreInvalidationReason()).isNull();
        assertThat(demand.getPreviousScoreSnapshot()).isNull();
    }

    @Test
    void approvedDemandCannotReturnToAnalysis() {
        UUID demandId = UUID.randomUUID();
        Demand demand = scoredDemand("APPROVED");
        demand.setId(demandId);
        DemandRepository demands = mock(DemandRepository.class);
        DemandScoreLifecycleService lifecycleMock = mock(DemandScoreLifecycleService.class);
        when(demands.findByIdAndDeletedAtIsNull(demandId)).thenReturn(Optional.of(demand));

        DemandService service = new DemandService(
                demands,
                mock(DemandAttachmentRepository.class),
                mock(DemandHistoryRepository.class),
                mock(DemandCodeService.class),
                mock(DemandHistoryService.class),
                mock(StrategicPlanService.class),
                mock(OperationalPlanService.class),
                mock(StrategicPillarService.class),
                mock(StrategicObjectiveService.class),
                mock(ProgramService.class),
                mock(CommitteeService.class),
                mock(CommitteeSuggestionService.class),
                mock(ProjectRepository.class),
                mock(LookupValueService.class),
                mock(DemandScoringService.class),
                lifecycleMock,
                mock(DemandPreScoreService.class));

        assertThatThrownBy(() -> service.changeStatus(demandId, new StatusPatch("IN_ANALYSIS", null), "tester"))
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("APPROVED_DEMAND_CANNOT_RETURN_TO_ANALYSIS");
                    assertThat(error.getMessage()).contains("não pode voltar para Em Análise");
                });
        verifyNoInteractions(lifecycleMock);
    }

    @Test
    void approvedDemandCannotBeScored() {
        UUID demandId = UUID.randomUUID();
        Demand demand = scoredDemand("APPROVED");
        demand.setId(demandId);
        DemandRepository demands = mock(DemandRepository.class);
        when(demands.findByIdAndDeletedAtIsNull(demandId)).thenReturn(Optional.of(demand));

        DemandScoringService scoringService = new DemandScoringService(
                demands,
                mock(DemandScoringRepository.class),
                mock(ScoringCriterionService.class),
                lifecycle);

        assertThatThrownBy(() -> scoringService.upsert(demandId, new UpsertRequest(List.of()), "tester"))
                .isInstanceOfSatisfying(DomainException.class, error ->
                        assertThat(error.getCode())
                                .isEqualTo("APPROVED_DEMAND_SCORE_RECALCULATION_NOT_ALLOWED"));
    }

    @Test
    void ranksDemandsGloballyAndWithinDirection() {
        Demand firstTechnology = rankedDemand("Tecnologia", "95.00");
        Demand firstOperations = rankedDemand("Operações", "90.00");
        Demand secondTechnology = rankedDemand(" tecnologia ", "85.00");
        Demand withoutDirection = rankedDemand(" ", "80.00");

        DemandScoringService scoringService = new DemandScoringService(
                mock(DemandRepository.class),
                mock(DemandScoringRepository.class),
                mock(ScoringCriterionService.class),
                lifecycle);

        scoringService.assignRanks(List.of(
                firstTechnology,
                firstOperations,
                secondTechnology,
                withoutDirection));

        assertThat(firstTechnology.getPortfolioRank()).isEqualTo(1);
        assertThat(firstOperations.getPortfolioRank()).isEqualTo(2);
        assertThat(secondTechnology.getPortfolioRank()).isEqualTo(3);
        assertThat(withoutDirection.getPortfolioRank()).isEqualTo(4);
        assertThat(firstTechnology.getDirectionRank()).isEqualTo(1);
        assertThat(firstOperations.getDirectionRank()).isEqualTo(1);
        assertThat(secondTechnology.getDirectionRank()).isEqualTo(2);
        assertThat(withoutDirection.getDirectionRank()).isNull();
    }

    @Test
    void invalidatedScoreMustBeFullyReplaced() {
        UUID demandId = UUID.randomUUID();
        Demand demand = scoredDemand("IN_ANALYSIS");
        demand.setId(demandId);
        lifecycle.applyStatusTransition(demand, "IN_PRIORITIZATION", "REJECTED", "Rejeitada");
        DemandRepository demands = mock(DemandRepository.class);
        DemandScoringRepository scoring = mock(DemandScoringRepository.class);
        ScoringCriterionService criteria = mock(ScoringCriterionService.class);
        ScoringCriterion requiredCriterion = new ScoringCriterion();
        requiredCriterion.setId(UUID.randomUUID());
        when(demands.findByIdAndDeletedAtIsNull(demandId)).thenReturn(Optional.of(demand));
        when(criteria.findActive()).thenReturn(List.of(requiredCriterion));

        DemandScoringService scoringService = new DemandScoringService(demands, scoring, criteria, lifecycle);
        UpsertRequest incompleteRequest = new UpsertRequest(List.of(
                new ScoreInput(UUID.randomUUID(), BigDecimal.ONE, null)));

        assertThatThrownBy(() -> scoringService.upsert(demandId, incompleteRequest, "tester"))
                .isInstanceOfSatisfying(BadRequestException.class, error ->
                        assertThat(error.getMessage()).contains("deve substituir todos os critérios ativos"));
    }

    @Test
    void approvalRequiresCurrentValidScore() {
        Demand demand = scoredDemand("READY_FOR_COMMITTEE");
        lifecycle.applyStatusTransition(demand, "READY_FOR_COMMITTEE", "IN_ANALYSIS", null);

        assertThatThrownBy(() -> lifecycle.applyStatusTransition(
                demand, "READY_FOR_COMMITTEE", "APPROVED", null))
                .isInstanceOfSatisfying(DomainException.class, error ->
                        assertThat(error.getCode()).isEqualTo("APPROVAL_REQUIRES_VALID_SCORE"));
    }

    @Test
    void conversionInheritsProjectFieldsFromDemand() {
        UUID demandId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Demand demand = scoredDemand("APPROVED");
        demand.setId(demandId);
        demand.setCode("DEM-2026-0001");
        demand.setTitle("Modernização OSS");
        demand.setDescription("Descrição da demanda");
        demand.setArea("Tecnologia");
        demand.setDirection("DIT");
        demand.setImpactedSystem("OSS");
        demand.setExpectedBenefit("Redução do tempo de operação");
        demand.setInitialPriority("HIGH");
        demand.setEstimatedBudget(new BigDecimal("450000.00"));
        demand.setDesiredDate(LocalDate.of(2027, 6, 30));

        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setName("Transformação Digital");
        program.setProgramManager("Gestor do Programa");
        demand.setProgram(program);

        LookupValue domain = new LookupValue();
        domain.setId(UUID.randomUUID());
        domain.setCategory("DEMAND_DOMAIN");
        domain.setCode("OSS");
        domain.setLabel("OSS");
        domain.setActive(true);
        demand.setDomain(domain);

        DemandRepository demands = mock(DemandRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        DemandHistoryService history = mock(DemandHistoryService.class);
        when(demands.findByIdAndDeletedAtIsNull(demandId)).thenReturn(Optional.of(demand));
        when(demands.save(any(Demand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projects.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(projectId);
            return project;
        });

        DemandService service = new DemandService(
                demands,
                mock(DemandAttachmentRepository.class),
                mock(DemandHistoryRepository.class),
                mock(DemandCodeService.class),
                history,
                mock(StrategicPlanService.class),
                mock(OperationalPlanService.class),
                mock(StrategicPillarService.class),
                mock(StrategicObjectiveService.class),
                mock(ProgramService.class),
                mock(CommitteeService.class),
                mock(CommitteeSuggestionService.class),
                projects,
                mock(LookupValueService.class),
                mock(DemandScoringService.class),
                lifecycle,
                mock(DemandPreScoreService.class));

        ConvertToProject request = new ConvertToProject(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);

        var response = service.convertToProject(demandId, request, "tester");

        Project project = demand.getConvertedProject();
        assertThat(project.getId()).isEqualTo(projectId);
        assertThat(project.getName()).isEqualTo("Modernização OSS");
        assertThat(project.getDescription()).isEqualTo("Descrição da demanda");
        assertThat(project.getProgram()).isSameAs(program);
        assertThat(project.getDomain()).isSameAs(domain);
        assertThat(project.getBusinessArea()).isEqualTo("Tecnologia");
        assertThat(project.getResponsibleDirection()).isEqualTo("DIT");
        assertThat(project.getProjectManager()).isEqualTo("Gestor do Programa");
        assertThat(project.getImpactedSystem()).isEqualTo("OSS");
        assertThat(project.getExpectedBenefits()).isEqualTo("Redução do tempo de operação");
        assertThat(project.getPlannedEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(project.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(project.getRanking()).isEqualTo(4);
        assertThat(project.getBudgetLine()).isEqualTo("OSS");
        assertThat(project.getBudget()).isEqualByComparingTo("450000.00");
        assertThat(project.getSourceDemandId()).isEqualTo(demandId);
        assertThat(response.project().domainId()).isEqualTo(domain.getId());
        assertThat(response.demand().scoreStatus()).isEqualTo("Invalidado");
    }

    private Demand scoredDemand(String status) {
        Demand demand = new Demand();
        demand.setStatus(status);
        demand.setScoreStatus("Válido");
        demand.setScoreTotal(new BigDecimal("82.50"));
        demand.setPortfolioRank(4);
        demand.setDirectionRank(2);
        demand.setScoreCalculatedAt(Instant.parse("2026-08-03T12:00:00Z"));
        return demand;
    }

    private Demand rankedDemand(String direction, String score) {
        Demand demand = new Demand();
        demand.setDirection(direction);
        demand.setScoreTotal(new BigDecimal(score));
        return demand;
    }
}
