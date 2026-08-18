package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ComplexityLevelConfig;
import cvt.cv.ppmbackend.enums.DemandComplexity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplexityLevelConfigRepository extends JpaRepository<ComplexityLevelConfig, DemandComplexity> {
}
