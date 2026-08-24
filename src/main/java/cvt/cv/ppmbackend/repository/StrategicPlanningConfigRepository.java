package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.StrategicPlanningConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StrategicPlanningConfigRepository extends JpaRepository<StrategicPlanningConfig, UUID> {
    Optional<StrategicPlanningConfig> findFirstByActiveTrueOrderByCreatedAtDesc();
}
