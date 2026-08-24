package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandScoring;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandScoringRepository extends JpaRepository<DemandScoring, UUID> {
    List<DemandScoring> findByPlanningCycle_IdOrderByCriterionOrderIndexAsc(UUID planningCycleId);

    Optional<DemandScoring> findByPlanningCycle_IdAndCriterion_Id(UUID planningCycleId, UUID criterionId);
}
