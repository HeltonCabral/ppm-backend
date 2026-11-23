package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandAreaRepository extends JpaRepository<DemandArea, UUID> {
    List<DemandArea> findByDemandIdOrderByCreatedAtAsc(UUID demandId);
    void deleteByDemandId(UUID demandId);
}
