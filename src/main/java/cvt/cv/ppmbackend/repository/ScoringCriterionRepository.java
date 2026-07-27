package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ScoringCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ScoringCriterionRepository extends JpaRepository<ScoringCriterion, UUID> {
    List<ScoringCriterion> findByActiveTrueOrderByOrderIndexAsc();

    List<ScoringCriterion> findByDimensionIdAndActiveTrueOrderByOrderIndexAsc(UUID dimensionId);
}
