package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DemandHistoryRepository extends JpaRepository<DemandHistory, UUID> {
    List<DemandHistory> findByDemandIdOrderByOccurredAtDesc(UUID demandId);
}
