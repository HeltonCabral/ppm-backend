package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.*;
import cvt.cv.ppmbackend.dto.ProjectResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectEvent;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectPhase;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.*;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Gestão de projetos com base de dados real: os 13 testes mínimos de PROJETOS-BACKEND.md. */
@DataJpaTest
class ProjectManagementJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectEventRepository eventRepository;
    @Autowired private ProjectPhaseRepository phaseRepository;
    @Autowired private ProjectMilestoneRepository milestoneRepository;
    @Autowired private ProjectStatusReportRepository reportRepository;
    @Autowired private ProjectRiskRepository riskRepository;
    @Autowired private ProjectChangeRequestRepository changeRequestRepository;
    @Autowired private ProjectClosureRepository closureRepository;
    @Autowired private ProjectLessonLearnedRepository lessonRepository;
    @Autowired private BenefitRepository benefitRepository;
    @Autowired private ProjectExecutionRankHistoryRepository rankHistory;
    @Autowired private LookupValueRepository lookups;
    @Autowired private SupplierRepository suppliers;
    @Autowired private ProgramRepository programs;
    @Autowired private StrategicPlanRepository strategicPlans;
    @Autowired private OperationalPlanRepository operationalPlans;
    @Autowired private StrategicPillarRepository strategicPillars;
    @Autowired private StrategicObjectiveRepository strategicObjectives;
    @Autowired private PhaseModelRepository phaseModels;
    @Autowired private DemandAreaRepository demandAreas;
    @Autowired private DemandParticipatingDirectionRepository participatingDirections;
    @Autowired private DemandParticipatingDirectionAreaRepository participatingDirectionAreas;
    @Autowired private DemandSuccessCriterionRepository successCriteria;
    @Autowired private DemandProfileRequirementRepository profileRequirements;

    private final AccessControlProperties properties = new AccessControlProperties();
    private final UserAccessService userAccess = mock(UserAccessService.class);
    private ProjectPhaseService phases;
    private ProjectStatusReportService reports;
    private ProjectRiskService risks;
    private ProjectChangeRequestService changeRequests;
    private ProjectMilestoneService milestones;
    private BenefitService benefits;
    private ProjectService projectService;
    private Project project;

    @BeforeEach
    void setUp() {
        ProjectEventService events = new ProjectEventService(eventRepository);
        ProjectGuard guard = new ProjectGuard(projects, properties, userAccess);
        phases = new ProjectPhaseService(phaseRepository, projects, guard, events);
        reports = new ProjectStatusReportService(reportRepository, projects, guard, phases, events);
        risks = new ProjectRiskService(riskRepository, projects, guard, events);
        changeRequests = new ProjectChangeRequestService(changeRequestRepository, projects, guard, phases, events);
        milestones = new ProjectMilestoneService(milestoneRepository, phaseRepository, guard, events);
        benefits = new BenefitService(benefitRepository, guard, events);
        DemandProjectInheritanceService inheritance = new DemandProjectInheritanceService(demandAreas,
                participatingDirections, participatingDirectionAreas, successCriteria,
                new DemandRequirementsResolver(new DemandResourceDerivationService(participatingDirections),
                        profileRequirements));
        projectService = new ProjectService(projects, rankHistory, lookups, suppliers, programs, strategicPlans,
                operationalPlans, strategicPillars, strategicObjectives, inheritance, phases, events,
                new ProjectClosureService(closureRepository, lessonRepository, guard), guard, phaseModels);

        project = new Project();
        project.setName("Portal de clientes");
        project.setStatus(ProjectStatus.PLANNED);
        project.setDirectionName("DSI");
        project.setBudget(new BigDecimal("1000.00"));
        ProjectExecution execution = new ProjectExecution();
        execution.setPlannedStartDate(LocalDate.of(2027, 1, 1));
        execution.setPlannedEndDate(LocalDate.of(2027, 4, 10));
        project.attachExecution(execution);
        phases.createPhases(project, List.of(
                new ProjectPhasePlanner.PhaseSeed("Desenho", 1, new BigDecimal("20")),
                new ProjectPhasePlanner.PhaseSeed("Desenvolvimento", 2, new BigDecimal("30")),
                new ProjectPhasePlanner.PhaseSeed("Piloto", 3, new BigDecimal("50"))), "cvt00983");
        project = projects.save(project);
        reload();
    }

    // 1. Conversão de uma demanda com planeamento personalizado → fases personalizadas, pelo peso.
    @Test
    void conversionCreatesTheCustomPhasesDistributedByWeight() {
        Demand demand = new Demand();
        demand.setCode("DEM-CUSTOM");
        demand.setTitle("Demanda personalizada");
        demand.setProblemStatement("p");
        demand.setDesiredOutcome("r");
        demand.setDirectionName("DSI");
        demand.setPlannedStartDate(LocalDate.of(2027, 1, 1));
        demand.setDesiredDate(LocalDate.of(2027, 4, 10));
        em.persist(demand);
        customPhase(demand, "Desenho", 1, "20");
        customPhase(demand, "Construção", 2, "80");
        em.flush();

        Project created = new Project();
        created.setName("Convertido");
        created.setDirectionName("DSI");
        Project saved = projectService.saveFromDemand(created, demand, new DemandPlanningCycle(),
                null, null, null, null, null, "cvt00983");
        reload();

        assertThat(phases.list(saved.getId()))
                .extracting(PhaseResponse::name, PhaseResponse::plannedStartDate, PhaseResponse::plannedEndDate)
                .containsExactly(
                        tuple("Desenho", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 20)),
                        tuple("Construção", LocalDate.of(2027, 1, 21), LocalDate.of(2027, 4, 10)));
        assertThat(eventTypes(saved)).contains(ProjectEventType.CREATED);
        assertThat(projectService.get(saved.getId()).baselineEndDate()).isEqualTo(LocalDate.of(2027, 4, 10));
    }

    // 2. Fase DONE → progresso 100, fim real hoje e progresso do projeto pelo peso.
    @Test
    void finishingAPhaseRecalculatesTheProjectProgress() {
        projectService.start(project.getId(), "cvt00983");
        ProjectPhase first = phase("Desenho");

        PhaseResponse done = phases.update(project.getId(), first.getId(),
                new PhaseUpdateRequest(ProjectPhaseStatus.DONE, null, null, null, null, null), "cvt00983");
        reload();

        assertThat(done.progress()).isEqualTo(100);
        assertThat(done.actualEndDate()).isEqualTo(LocalDate.now());
        ProjectResponse response = projectService.get(project.getId());
        assertThat(response.execution().progress()).isEqualTo(20);
        assertThat(eventTypes(project)).contains(ProjectEventType.STARTED, ProjectEventType.PHASE_UPDATED);

        phases.update(project.getId(), phase("Desenvolvimento").getId(),
                new PhaseUpdateRequest(ProjectPhaseStatus.IN_PROGRESS, 50, null, null, null, null), "cvt00983");
        reload();
        assertThat(projectService.get(project.getId()).execution().progress()).isEqualTo(35);
        assertThat(projectService.get(project.getId()).currentPhaseName()).isEqualTo("Desenvolvimento");
    }

    @Test
    void startingTheProjectStartsTheFirstPhase() {
        projectService.start(project.getId(), "cvt00983");
        reload();

        assertThat(phases.list(project.getId())).extracting(PhaseResponse::status)
                .containsExactly(ProjectPhaseStatus.IN_PROGRESS, ProjectPhaseStatus.NOT_STARTED,
                        ProjectPhaseStatus.NOT_STARTED);
        assertThat(projectService.get(project.getId()).currentPhaseName()).isEqualTo("Desenho");
    }

    // 3. Relatório RED sem bloqueios nem próximos passos → 400; válido → execução atualizada e evento.
    @Test
    void statusReportFeedsTheExecution() {
        projectService.start(project.getId(), "cvt00983");
        assertThatThrownBy(() -> reports.create(project.getId(), report(ExecutiveStatus.RED, null, null), "x"))
                .isInstanceOf(BadRequestException.class);

        StatusReportResponse created = reports.create(project.getId(),
                report(ExecutiveStatus.YELLOW, "Atraso do fornecedor", null), "cvt00983");
        reload();

        ProjectResponse response = projectService.get(project.getId());
        assertThat(created.progress()).isEqualTo(40);
        assertThat(response.execution().progress()).isEqualTo(40);
        assertThat(response.execution().consumedBudget()).isEqualByComparingTo("250.00");
        assertThat(response.execution().scheduleStatus()).isEqualTo(ExecutiveStatus.YELLOW);
        assertThat(response.execution().valueStatus()).isEqualTo(ExecutiveStatus.GREEN);
        assertThat(response.lastReportStatus()).isEqualTo(ExecutiveStatus.YELLOW);
        assertThat(response.needsSupport()).isTrue();
        assertThat(eventTypes(project)).contains(ProjectEventType.REPORT_SUBMITTED,
                ProjectEventType.SUPPORT_REQUESTED, ProjectEventType.PROGRESS_OVERRIDDEN);
    }

    // 4. Relatório num projeto PLANNED → 409.
    @Test
    void statusReportNeedsTheProjectInProgress() {
        assertConflict(() -> reports.create(project.getId(), report(ExecutiveStatus.GREEN, null, null), "x"));
    }

    // 5. Risco 4×4 sem resposta → 400; crítico → execution.risk CRITICAL; fechado → volta ao maior aberto.
    // 6. ISSUE com probabilidade 2 → gravado com 5.
    @Test
    void risksDriveTheAggregatedProjectRisk() {
        assertThatThrownBy(() -> risks.create(project.getId(), risk(ProjectRiskKind.RISK, 4, 4, null), "x"))
                .isInstanceOf(BadRequestException.class);

        RiskResponse medium = risks.create(project.getId(), risk(ProjectRiskKind.RISK, 2, 3, "Plano B"), "x");
        RiskResponse critical = risks.create(project.getId(), risk(ProjectRiskKind.RISK, 4, 4, "Mitigar"), "x");
        reload();
        assertThat(critical.level()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(projectService.get(project.getId()).execution().risk()).isEqualTo(RiskLevel.CRITICAL);

        RiskRequest closing = new RiskRequest(ProjectRiskKind.RISK, "Risco 4x4", null, 4, 4,
                ProjectRiskStatus.CLOSED, null, null, null);
        risks.update(project.getId(), critical.id(), closing, "x");
        reload();
        assertThat(projectService.get(project.getId()).execution().risk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(medium.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(eventTypes(project)).contains(ProjectEventType.RISK_OPENED, ProjectEventType.RISK_CLOSED);

        RiskResponse issue = risks.create(project.getId(), risk(ProjectRiskKind.ISSUE, 2, 2, "Corrigir"), "x");
        assertThat(issue.probability()).isEqualTo(5);
        assertThat(risks.search(Set.of(ProjectRiskStatus.OPEN), Set.of(RiskLevel.MEDIUM)))
                .extracting(RiskResponse::projectCode).isNotEmpty();
    }

    // 7. Pedido BUDGET aprovado → orçamento alterado, valor antigo no pedido e evento.
    // 9. Segundo pedido BUDGET com outro pendente → 409.
    @Test
    void approvedBudgetChangeUpdatesTheProject() {
        ChangeRequestResponse request = changeRequests.create(project.getId(),
                new ChangeRequestCreate(ChangeRequestKind.BUDGET, "Mais licenças", "Novo âmbito",
                        new BigDecimal("1500.00"), null, null, null), "cvt00983");
        assertConflict(() -> changeRequests.create(project.getId(),
                new ChangeRequestCreate(ChangeRequestKind.BUDGET, "Outro", "x", new BigDecimal("2000"), null, null,
                        null), "cvt00983"));

        ChangeRequestResponse approved = changeRequests.approve(project.getId(), request.id(),
                new DecisionRequest("Ok"), "pmo");
        reload();

        assertThat(approved.status()).isEqualTo(ChangeRequestStatus.APPROVED);
        assertThat(approved.currentBudget()).isEqualByComparingTo("1000.00");
        assertThat(projects.findById(project.getId()).orElseThrow().getBudget()).isEqualByComparingTo("1500.00");
        assertThat(eventTypes(project)).contains(ProjectEventType.CHANGE_REQUESTED, ProjectEventType.CHANGE_APPROVED);
        assertConflict(() -> changeRequests.approve(project.getId(), request.id(), new DecisionRequest("2.ª"), "pmo"));
    }

    // 8. Pedido SCHEDULE aprovado → fim alterado e fases não concluídas redistribuídas.
    @Test
    void approvedScheduleChangeRedistributesTheOpenPhases() {
        projectService.start(project.getId(), "cvt00983");
        phases.update(project.getId(), phase("Desenho").getId(), new PhaseUpdateRequest(ProjectPhaseStatus.DONE,
                null, null, null, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 20)), "x");
        PhaseResponse designBefore = phases.list(project.getId()).get(0);
        ChangeRequestResponse request = changeRequests.create(project.getId(),
                new ChangeRequestCreate(ChangeRequestKind.SCHEDULE, "Atraso", "Fornecedor", null,
                        LocalDate.of(2027, 6, 30), null, null), "x");

        changeRequests.approve(project.getId(), request.id(), new DecisionRequest(null), "pmo");
        reload();

        List<PhaseResponse> after = phases.list(project.getId());
        assertThat(projectService.get(project.getId()).execution().plannedEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(after.get(0).plannedEndDate()).isEqualTo(designBefore.plannedEndDate());
        assertThat(after.get(2).plannedEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(after.get(1).plannedStartDate()).isEqualTo(LocalDate.of(2027, 1, 21));
        assertThat(after.get(2).plannedStartDate()).isEqualTo(after.get(1).plannedEndDate().plusDays(1));
    }

    // 10. Rejeitar sem nota → 400; aprovar sem projeto_editar_todos (filtro ligado) → 403.
    @Test
    void decisionsNeedANoteToRejectAndEditAllToApprove() {
        ChangeRequestResponse request = changeRequests.create(project.getId(),
                new ChangeRequestCreate(ChangeRequestKind.SCOPE, "Novo relatório", "Pedido da direção", null, null,
                        "Acrescentar relatório mensal", null), "cvt00983");
        assertThatThrownBy(() -> changeRequests.reject(project.getId(), request.id(), new DecisionRequest(" "), "pmo"))
                .isInstanceOf(BadRequestException.class);

        properties.getProjectFilter().setEnabled(true);
        when(userAccess.current()).thenReturn(new UserAccess("cvt00983", Set.of(), List.of()));

        assertThatThrownBy(() -> changeRequests.approve(project.getId(), request.id(), new DecisionRequest("ok"), "x"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
    }

    // 11. complete com closure sem lições → 400; com closure válido → relatório, fases fechadas, 100%.
    @Test
    void completingWithAClosureRecordsItAndClosesThePhases() {
        projectService.start(project.getId(), "cvt00983");
        assertThatThrownBy(() -> projectService.complete(project.getId(),
                new ClosureRequest("Entregue", ObjectivesMet.YES, List.of(), null), "x"))
                .isInstanceOf(BadRequestException.class);

        projectService.complete(project.getId(), new ClosureRequest("Entregue em produção", ObjectivesMet.PARTIAL,
                List.of("Envolver operações cedo", " ", "Testes de carga mais cedo"), "Equipa OMS"), "cvt00983");
        reload();

        ProjectClosureService closures = new ProjectClosureService(closureRepository, lessonRepository,
                new ProjectGuard(projects, properties, userAccess));
        ClosureResponse closure = closures.get(project.getId());
        assertThat(closure.lessonsLearned()).containsExactly("Envolver operações cedo", "Testes de carga mais cedo");
        assertThat(closures.allLessons()).extracting(LessonLearnedResponse::projectName).contains("Portal de clientes");
        assertThat(phases.list(project.getId())).allMatch(p -> p.status() == ProjectPhaseStatus.DONE);
        assertThat(projectService.get(project.getId()).execution().progress()).isEqualTo(100);
        assertThat(eventTypes(project)).contains(ProjectEventType.COMPLETED);
    }

    @Test
    void completingWithoutBodyKeepsTheCurrentBehaviour() {
        projectService.start(project.getId(), "cvt00983");
        ProjectResponse completed = projectService.complete(project.getId(), null, "x");

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.execution().progress()).isEqualTo(100);
        assertThat(closureRepository.findByProjectId(project.getId())).isEmpty();
    }

    // 12. Escritas num projeto COMPLETED → 409, exceto benefícios. 13. Benefícios devolvem o DTO.
    @Test
    void closedProjectRejectsWritesExceptBenefits() {
        projectService.start(project.getId(), "x");
        projectService.complete(project.getId(), null, "x");
        reload();

        assertConflict(() -> phases.update(project.getId(), phase("Piloto").getId(),
                new PhaseUpdateRequest(ProjectPhaseStatus.IN_PROGRESS, 10, null, null, null, null), "x"));
        assertConflict(() -> milestones.create(project.getId(), milestone("Go-live"), "x"));
        assertConflict(() -> risks.create(project.getId(), risk(ProjectRiskKind.RISK, 1, 1, null), "x"));
        assertConflict(() -> changeRequests.create(project.getId(), new ChangeRequestCreate(ChangeRequestKind.SCOPE,
                "x", "x", null, null, "x", null), "x"));

        BenefitResponse benefit = benefits.create(new BenefitRequest(project.getId(), BenefitType.EFFICIENCY, null,
                new BigDecimal("300"), LocalDate.now(), "Medido após o fecho"), "cvt00983");
        assertThat(benefit.createdBy()).isEqualTo("cvt00983");
        assertThat(benefits.findByProject(project.getId())).extracting(BenefitResponse::projectName)
                .containsExactly("Portal de clientes");
        assertThatThrownBy(() -> benefits.create(new BenefitRequest(project.getId(), BenefitType.EFFICIENCY, null,
                null, LocalDate.now(), null), "x")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void milestonesAreCompletedTodayAndFlagLateOnes() {
        MilestoneResponse late = milestones.create(project.getId(), new MilestoneRequest("Kick-off", null,
                phase("Desenho").getId(), LocalDate.now().minusDays(1), MilestoneStatus.PENDING, null, false,
                "PMO"), "x");
        MilestoneResponse done = milestones.update(project.getId(), late.id(), new MilestoneRequest("Kick-off", null,
                null, LocalDate.now().minusDays(1), MilestoneStatus.DONE, null, true, "PMO"), "x");

        assertThat(late.late()).isTrue();
        assertThat(late.phaseName()).isEqualTo("Desenho");
        assertThat(done.late()).isFalse();
        assertThat(done.completedAt()).isEqualTo(LocalDate.now());
        assertThat(eventTypes(project)).contains(ProjectEventType.MILESTONE_ADDED, ProjectEventType.MILESTONE_DONE);
    }

    private StatusReportRequest report(ExecutiveStatus overall, String blockers, String nextSteps) {
        return new StatusReportRequest(LocalDate.now(), overall, overall, ExecutiveStatus.GREEN,
                ExecutiveStatus.GREEN, ExecutiveStatus.GREEN, 40, new BigDecimal("250.00"), "Bom ritmo", null,
                nextSteps, blockers, true);
    }

    private static RiskRequest risk(ProjectRiskKind kind, int probability, int impact, String response) {
        return new RiskRequest(kind, "Risco " + probability + "x" + impact, null, probability, impact, null, response,
                null, null);
    }

    private static MilestoneRequest milestone(String name) {
        return new MilestoneRequest(name, null, null, LocalDate.now().plusDays(10), MilestoneStatus.PENDING, null,
                true, null);
    }

    private ProjectPhase phase(String name) {
        return phaseRepository.findByProjectIdOrderBySequenceAsc(project.getId()).stream()
                .filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    private List<ProjectEventType> eventTypes(Project p) {
        return eventRepository.findByProjectIdOrderByAtDesc(p.getId()).stream().map(ProjectEvent::getType).toList();
    }

    private void assertConflict(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    private void customPhase(Demand demand, String name, int sequence, String weight) {
        DemandCustomPhase phase = new DemandCustomPhase();
        phase.setDemand(demand);
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal(weight));
        em.persist(phase);
        demand.getCustomPhases().add(phase);
    }

    private void reload() {
        em.flush();
        em.clear();
    }
}
