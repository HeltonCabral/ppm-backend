package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Constrói o pool de Demandas sobre o qual os cenários operam.
 *
 * <p>Distinguem-se dois conjuntos:
 * <ul>
 *   <li><b>elegível</b> — apenas ciclos PRIORITIZED, os únicos que podem entrar na composição;</li>
 *   <li><b>portfólio</b> — inclui também IN_REVIEW (visíveis mas não integráveis) e as Demandas já
 *       decididas (IN_PLAN/BACKLOG), para que um cenário FINAL continue consultável.</li>
 * </ul>
 */
@Service
public class ScenarioPoolService {

    public static final List<DemandPlanningStatus> ELIGIBLE_STATUSES = List.of(DemandPlanningStatus.PRIORITIZED);

    public static final List<DemandPlanningStatus> PORTFOLIO_STATUSES = List.of(
            DemandPlanningStatus.PRIORITIZED, DemandPlanningStatus.IN_REVIEW,
            DemandPlanningStatus.IN_PLAN, DemandPlanningStatus.BACKLOG);

    private final DemandPlanningCycleRepository planningCycles;
    private final DemandDependencyRepository dependencies;

    public ScenarioPoolService(DemandPlanningCycleRepository planningCycles,
            DemandDependencyRepository dependencies) {
        this.planningCycles = planningCycles;
        this.dependencies = dependencies;
    }

    @Transactional(readOnly = true)
    public List<ScenarioDemandSnapshot> eligiblePool(UUID strategicPlanId) {
        return snapshotsOf(planningCycles.findAllByStrategicPlan_IdAndStatusIn(strategicPlanId, ELIGIBLE_STATUSES));
    }

    @Transactional(readOnly = true)
    public List<ScenarioDemandSnapshot> portfolioPool(UUID strategicPlanId) {
        return snapshotsOf(planningCycles.findAllByStrategicPlan_IdAndStatusIn(strategicPlanId, PORTFOLIO_STATUSES));
    }

    /** Conta as dependências (totais e bloqueantes) de cada Demanda numa única consulta. */
    public List<ScenarioDemandSnapshot> snapshotsOf(List<DemandPlanningCycle> cycles) {
        if (cycles.isEmpty()) {
            return List.of();
        }
        Set<UUID> demandIds = cycles.stream().map(c -> c.getDemand().getId()).collect(Collectors.toSet());
        Map<UUID, Integer> total = new HashMap<>();
        Map<UUID, Integer> blocking = new HashMap<>();
        for (DemandDependency dependency : dependencies.findByDemand_IdIn(demandIds)) {
            UUID demandId = dependency.getDemand().getId();
            total.merge(demandId, 1, Integer::sum);
            if (dependency.getType() == DemandDependencyType.BLOCKING) {
                blocking.merge(demandId, 1, Integer::sum);
            }
        }
        return cycles.stream()
                .sorted(Comparator.comparing(
                        (DemandPlanningCycle c) -> c.getPortfolioRank() == null ? Integer.MAX_VALUE
                                : c.getPortfolioRank())
                        .thenComparing(c -> c.getDemand().getCode(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(cycle -> new ScenarioDemandSnapshot(cycle,
                        total.getOrDefault(cycle.getDemand().getId(), 0),
                        blocking.getOrDefault(cycle.getDemand().getId(), 0)))
                .toList();
    }
}
