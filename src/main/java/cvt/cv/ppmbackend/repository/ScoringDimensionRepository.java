package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ScoringDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringDimensionRepository extends JpaRepository<ScoringDimension, UUID> {
    Optional<ScoringDimension> findByCodeIgnoreCase(String code);

    Optional<ScoringDimension> findByCodeIgnoreCaseAndActiveTrue(String code);

    List<ScoringDimension> findByActiveTrueOrderByCodeAsc();

    @Query("select distinct d from ScoringDimension d left join fetch d.criteria order by d.code asc")
    List<ScoringDimension> findAllWithCriteriaOrderByCodeAsc();
}
