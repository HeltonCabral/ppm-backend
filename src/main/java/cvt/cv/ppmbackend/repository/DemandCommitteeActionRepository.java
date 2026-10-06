package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandCommitteeAction;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DemandCommitteeActionRepository extends JpaRepository<DemandCommitteeAction, UUID> {

    List<DemandCommitteeAction> findByPlanningCycle_IdOrderByRequestedAtDesc(UUID planningCycleId);

    List<DemandCommitteeAction> findByPlanningCycle_Demand_IdOrderByRequestedAtDesc(UUID demandId);

    Optional<DemandCommitteeAction> findFirstByPlanningCycle_IdAndResolvedAtIsNullOrderByRequestedAtDesc(
            UUID planningCycleId);

    @Query("select distinct action.planningCycle.id from DemandCommitteeAction action "
            + "where action.planningCycle.strategicPlan.id = :strategicPlanId and action.resolvedAt is null")
    Set<UUID> findCycleIdsUnderReview(@Param("strategicPlanId") UUID strategicPlanId);

    /**
     * Demandas que pediram revisão e ainda não concluíram o ciclo de revisão: têm uma ação
     * REQUEST_REVISION e o ciclo ainda não voltou a um estado de revisão concluída (ex.: PRIORITIZED).
     * {@code strategicPlanId} nulo devolve o conjunto global.
     */
    @Query("select distinct action.planningCycle.demand.id from DemandCommitteeAction action "
            + "where action.action = cvt.cv.ppmbackend.enums.CommitteeActionType.REQUEST_REVISION "
            + "and action.planningCycle.status not in :settledStatuses "
            + "and (:strategicPlanId is null or action.planningCycle.strategicPlan.id = :strategicPlanId)")
    Set<UUID> findDemandIdsPendingReview(
            @Param("settledStatuses") Collection<DemandPlanningStatus> settledStatuses,
            @Param("strategicPlanId") UUID strategicPlanId);
}
