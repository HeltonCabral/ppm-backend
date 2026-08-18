package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ComplexityCriterionConfig;
import cvt.cv.ppmbackend.enums.ComplexityCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ComplexityCriterionConfigRepository extends JpaRepository<ComplexityCriterionConfig, UUID> {
    Optional<ComplexityCriterionConfig> findByCriterion(ComplexityCriterion criterion);
}
