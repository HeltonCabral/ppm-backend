package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandPlanningCycleRepository
        extends JpaRepository<DemandPlanningCycle, UUID>, JpaSpecificationExecutor<DemandPlanningCycle> {

    Optional<DemandPlanningCycle> findByDemand_IdAndStrategicPlan_Id(UUID demandId, UUID strategicPlanId);

    long countByStrategicElement_Id(UUID strategicElementId);

    long countByStrategicPillar_Id(UUID strategicPillarId);

    Optional<DemandPlanningCycle> findByDemand_IdAndOperationalPlan_Id(UUID demandId, UUID operationalPlanId);

    List<DemandPlanningCycle> findAllByDemand_IdOrderByCycleEntryAtDesc(UUID demandId);

    List<DemandPlanningCycle> findAllByStrategicPlan_Id(UUID strategicPlanId);

    List<DemandPlanningCycle> findAllByOperationalPlan_Id(UUID operationalPlanId);

    List<DemandPlanningCycle> findAllByOperationalPlan_IdAndStatus(UUID operationalPlanId, DemandPlanningStatus status);

    List<DemandPlanningCycle> findAllByStrategicPlan_IdAndStatusIn(UUID strategicPlanId, List<DemandPlanningStatus> statuses);

    boolean existsByDemand_IdAndResponsibleCommittee_Id(UUID demandId, UUID committeeId);

    boolean existsByResponsibleCommittee_Id(UUID committeeId);
}
