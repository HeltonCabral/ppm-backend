package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.OperationalPlanBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OperationalPlanBaselineRepository extends JpaRepository<OperationalPlanBaseline, UUID> {
    List<OperationalPlanBaseline> findByOperationalPlan_IdOrderByCapturedAtDesc(UUID operationalPlanId);
}
