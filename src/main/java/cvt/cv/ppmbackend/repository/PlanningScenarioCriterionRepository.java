package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.PlanningScenarioCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanningScenarioCriterionRepository extends JpaRepository<PlanningScenarioCriterion, UUID> {

    List<PlanningScenarioCriterion> findByScenario_IdOrderByDisplayOrderAsc(UUID scenarioId);

    Optional<PlanningScenarioCriterion> findByIdAndScenario_Id(UUID id, UUID scenarioId);

    long countByScenario_Id(UUID scenarioId);
}
