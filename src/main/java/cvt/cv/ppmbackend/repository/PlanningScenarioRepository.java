package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.PlanningScenario;
import cvt.cv.ppmbackend.enums.ScenarioStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanningScenarioRepository extends JpaRepository<PlanningScenario, UUID> {

    List<PlanningScenario> findByStrategicPlan_IdOrderByCreatedAtDesc(UUID strategicPlanId);

    List<PlanningScenario> findByStrategicPlan_IdAndStatusOrderByCreatedAtDesc(UUID strategicPlanId,
            ScenarioStatus status);

    Optional<PlanningScenario> findFirstByStrategicPlan_IdAndStatus(UUID strategicPlanId, ScenarioStatus status);

    boolean existsByStrategicPlan_IdAndStatus(UUID strategicPlanId, ScenarioStatus status);

    boolean existsByStrategicPlan_IdAndNameIgnoreCase(UUID strategicPlanId, String name);
}
