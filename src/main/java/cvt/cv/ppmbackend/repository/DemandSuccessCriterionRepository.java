package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandSuccessCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandSuccessCriterionRepository extends JpaRepository<DemandSuccessCriterion, UUID> {
    List<DemandSuccessCriterion> findByDemandIdOrderByDisplayOrderAsc(UUID demandId);
    void deleteByDemandId(UUID demandId);
}
