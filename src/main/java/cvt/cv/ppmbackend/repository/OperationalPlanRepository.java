package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.OperationalPlan;
import cvt.cv.ppmbackend.enums.OperationalPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationalPlanRepository extends JpaRepository<OperationalPlan, UUID> {
    List<OperationalPlan> findByStrategicPlan_Id(UUID strategicPlanId);

    List<OperationalPlan> findByFiscalYear(Integer fiscalYear);

    List<OperationalPlan> findByStatus(OperationalPlanStatus status);

    Optional<OperationalPlan> findByStrategicPlan_IdAndFiscalYear(UUID strategicPlanId, Integer fiscalYear);
}
