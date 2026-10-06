package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandPlanningCycleScoreHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandPlanningCycleScoreHistoryRepository
        extends JpaRepository<DemandPlanningCycleScoreHistory, UUID> {
    List<DemandPlanningCycleScoreHistory> findByPlanningCycle_IdOrderByInvalidatedAtDesc(UUID planningCycleId);
}
