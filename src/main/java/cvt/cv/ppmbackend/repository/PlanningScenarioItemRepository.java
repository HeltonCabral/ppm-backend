package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.PlanningScenarioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanningScenarioItemRepository extends JpaRepository<PlanningScenarioItem, UUID> {

    List<PlanningScenarioItem> findByScenario_Id(UUID scenarioId);

    List<PlanningScenarioItem> findByScenario_IdAndIncludedTrueOrderByScenarioRankAsc(UUID scenarioId);

    Optional<PlanningScenarioItem> findByScenario_IdAndDemandPlanningCycle_Id(UUID scenarioId, UUID planningCycleId);

    void deleteByScenario_Id(UUID scenarioId);
}
