package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface DemandRepository extends JpaRepository<Demand, UUID>, JpaSpecificationExecutor<Demand> {
    Optional<Demand> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCode(String code);
}
