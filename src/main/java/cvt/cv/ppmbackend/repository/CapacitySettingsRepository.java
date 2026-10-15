package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.CapacitySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CapacitySettingsRepository extends JpaRepository<CapacitySettings, UUID> {
    Optional<CapacitySettings> findFirstByOrderByIdAsc();
}
