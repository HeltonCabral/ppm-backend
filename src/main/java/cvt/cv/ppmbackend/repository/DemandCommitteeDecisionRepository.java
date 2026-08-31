package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandCommitteeDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DemandCommitteeDecisionRepository extends JpaRepository<DemandCommitteeDecision, UUID> {

    List<DemandCommitteeDecision> findByPlanningCycle_IdOrderByDecisionAtDesc(UUID planningCycleId);

    List<DemandCommitteeDecision> findByPlanningCycle_Demand_IdOrderByDecisionAtDesc(UUID demandId);

    @Query("select distinct decision.planningCycle.id from DemandCommitteeDecision decision "
            + "where decision.planningCycle.strategicPlan.id = :strategicPlanId")
    Set<UUID> findDecidedCycleIdsByStrategicPlan(@Param("strategicPlanId") UUID strategicPlanId);
}
