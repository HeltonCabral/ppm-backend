package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.AnalysisResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ApplyCriteriaRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ApplyCriteriaResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CompareRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CompareResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CriterionRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.CriterionResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.DuplicateRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ExcludeRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.FinalizeRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.FinalizeResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.IncludeRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ReorderRequest;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ReorderResponse;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.Response;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.ScenarioDemandPage;
import cvt.cv.ppmbackend.dto.PlanningScenarioDtos.UpdateRequest;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import cvt.cv.ppmbackend.enums.DemandNature;
import cvt.cv.ppmbackend.enums.DemandScenarioSituation;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import cvt.cv.ppmbackend.service.PlanningScenarioAnalysisService;
import cvt.cv.ppmbackend.service.PlanningScenarioAnalysisService.ScenarioDemandFilters;
import cvt.cv.ppmbackend.service.PlanningScenarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/planning-scenarios")
public class PlanningScenarioController {

    private final PlanningScenarioService scenarios;
    private final PlanningScenarioAnalysisService analysis;

    public PlanningScenarioController(PlanningScenarioService scenarios,
            PlanningScenarioAnalysisService analysis) {
        this.scenarios = scenarios;
        this.analysis = analysis;
    }

    private String actor(String header) {
        return header == null || header.isBlank() ? "system" : header;
    }

    // ------------------------------------------------------------------- CRUD

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return scenarios.create(request, actor(user));
    }

    @GetMapping
    public List<Response> list(@RequestParam UUID strategicPlanId,
            @RequestParam(required = false) ScenarioStatus status) {
        return scenarios.list(strategicPlanId, status);
    }

    @GetMapping("/{scenarioId}")
    public Response get(@PathVariable UUID scenarioId) {
        return scenarios.get(scenarioId);
    }

    @PutMapping("/{scenarioId}")
    public Response update(@PathVariable UUID scenarioId, @Valid @RequestBody UpdateRequest request) {
        return scenarios.update(scenarioId, request);
    }

    @PostMapping("/{scenarioId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public Response duplicate(@PathVariable UUID scenarioId, @Valid @RequestBody DuplicateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return scenarios.duplicate(scenarioId, request, actor(user));
    }

    @DeleteMapping("/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID scenarioId) {
        scenarios.archive(scenarioId);
    }

    // -------------------------------------------------------------- critérios

    @GetMapping("/{scenarioId}/criteria")
    public List<CriterionResponse> listCriteria(@PathVariable UUID scenarioId) {
        return scenarios.listCriteria(scenarioId);
    }

    @PostMapping("/{scenarioId}/criteria")
    @ResponseStatus(HttpStatus.CREATED)
    public CriterionResponse addCriterion(@PathVariable UUID scenarioId,
            @Valid @RequestBody CriterionRequest request) {
        return scenarios.addCriterion(scenarioId, request);
    }

    @PutMapping("/{scenarioId}/criteria/{criterionId}")
    public CriterionResponse updateCriterion(@PathVariable UUID scenarioId, @PathVariable UUID criterionId,
            @Valid @RequestBody CriterionRequest request) {
        return scenarios.updateCriterion(scenarioId, criterionId, request);
    }

    @DeleteMapping("/{scenarioId}/criteria/{criterionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCriterion(@PathVariable UUID scenarioId, @PathVariable UUID criterionId) {
        scenarios.deleteCriterion(scenarioId, criterionId);
    }

    @PostMapping("/{scenarioId}/criteria/apply")
    public ApplyCriteriaResponse applyCriteria(@PathVariable UUID scenarioId,
            @RequestBody(required = false) ApplyCriteriaRequest request) {
        return scenarios.applyCriteria(scenarioId, request);
    }

    // ------------------------------------------------------------------ itens

    @PostMapping("/{scenarioId}/demands/include")
    public ApplyCriteriaResponse include(@PathVariable UUID scenarioId, @Valid @RequestBody IncludeRequest request) {
        return scenarios.include(scenarioId, request);
    }

    @PostMapping("/{scenarioId}/demands/exclude")
    public ApplyCriteriaResponse exclude(@PathVariable UUID scenarioId, @Valid @RequestBody ExcludeRequest request) {
        return scenarios.exclude(scenarioId, request);
    }

    @PostMapping("/{scenarioId}/demands/reorder")
    public ReorderResponse reorder(@PathVariable UUID scenarioId, @Valid @RequestBody ReorderRequest request) {
        return scenarios.reorder(scenarioId, request);
    }

    /**
     * Pool completo do portfólio com a situação de cada Demanda no cenário.
     * Ordenação fixa: INCLUDED (por scenarioRank), IN_REVIEW e EXCLUDED (por portfolioRank).
     */
    @GetMapping("/{scenarioId}/demands")
    public ScenarioDemandPage demands(@PathVariable UUID scenarioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) DemandScenarioSituation situation,
            @RequestParam(required = false) String directionCode,
            @RequestParam(required = false) DemandNature nature,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) BigDecimal scoreMin,
            @RequestParam(required = false) BigDecimal scoreMax,
            @RequestParam(required = false) DemandComplexity complexity,
            @RequestParam(required = false) String capacityStatus,
            @RequestParam(required = false) LocalDate mandatoryDeadlineUntil,
            @RequestParam(required = false) Integer blockingDependenciesMin,
            @RequestParam(required = false) Integer blockingDependenciesMax,
            @RequestParam(required = false) String search) {
        ScenarioDemandFilters filters = new ScenarioDemandFilters(situation, directionCode, nature, type, domain,
                scoreMin, scoreMax, complexity, capacityStatus, mandatoryDeadlineUntil,
                blockingDependenciesMin, blockingDependenciesMax, search);
        return analysis.demands(scenarioId, filters, page, size);
    }

    /**
     * Pool ao nível do plano, sobre todas as Demandas priorizadas + em revisão. Sem cenário: nada
     * fica de fora, por isso nenhuma linha vem como EXCLUDED e scenarioRank/inclusionSource/comment
     * vêm sempre a null.
     */
    @GetMapping("/plan/{strategicPlanId}/demands")
    public ScenarioDemandPage planDemands(@PathVariable UUID strategicPlanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) DemandScenarioSituation situation,
            @RequestParam(required = false) String directionCode,
            @RequestParam(required = false) DemandNature nature,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) BigDecimal scoreMin,
            @RequestParam(required = false) BigDecimal scoreMax,
            @RequestParam(required = false) DemandComplexity complexity,
            @RequestParam(required = false) String capacityStatus,
            @RequestParam(required = false) LocalDate mandatoryDeadlineUntil,
            @RequestParam(required = false) Integer blockingDependenciesMin,
            @RequestParam(required = false) Integer blockingDependenciesMax,
            @RequestParam(required = false) String search) {
        ScenarioDemandFilters filters = new ScenarioDemandFilters(situation, directionCode, nature, type, domain,
                scoreMin, scoreMax, complexity, capacityStatus, mandatoryDeadlineUntil,
                blockingDependenciesMin, blockingDependenciesMax, search);
        return analysis.planDemands(strategicPlanId, filters, page, size);
    }

    // ---------------------------------------------------- análise e comparação

    @GetMapping("/{scenarioId}/analysis")
    public AnalysisResponse analysis(@PathVariable UUID scenarioId) {
        return analysis.analysis(scenarioId);
    }

    /**
     * Análise ao nível do plano, sobre todas as demandas priorizadas. Não persiste nada e não
     * exige cenário — os campos {@code scenario} e {@code criteria} vêm vazios.
     */
    @GetMapping("/plan/{strategicPlanId}/analysis")
    public AnalysisResponse planAnalysis(@PathVariable UUID strategicPlanId) {
        return analysis.planAnalysis(strategicPlanId);
    }

    @PostMapping("/compare")
    public CompareResponse compare(@Valid @RequestBody CompareRequest request) {
        return analysis.compare(request.scenarioIds());
    }

    // -------------------------------------------------------------- finalizar

    @PostMapping("/{scenarioId}/finalize")
    public FinalizeResponse finalizeScenario(@PathVariable UUID scenarioId,
            @RequestBody(required = false) FinalizeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return scenarios.finalizeScenario(scenarioId, request, actor(user), actor(user));
    }
}
