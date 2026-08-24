package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StrategicPlanRepository extends JpaRepository<StrategicPlan, UUID>, JpaSpecificationExecutor<StrategicPlan> {
    Optional<StrategicPlan> findFirstByStatus(StrategicPlanStatus status);
    boolean existsByNameIgnoreCaseAndStartYearAndEndYearAndDeletedAtIsNull(String name,Integer startYear,Integer endYear);
    List<StrategicPlan> findAllByDeletedAtIsNullOrderByStartYearDesc();
    boolean existsByStatusInAndDeletedAtIsNull(List<StrategicPlanStatus> statuses);
    Optional<StrategicPlan> findFirstByStatusInAndDeletedAtIsNull(List<StrategicPlanStatus> statuses);
    Optional<StrategicPlan> findFirstByStatusAndDeletedAtIsNull(StrategicPlanStatus status);
}
