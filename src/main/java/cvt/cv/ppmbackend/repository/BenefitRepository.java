package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Benefit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BenefitRepository extends JpaRepository<Benefit, java.util.UUID> {
    List<Benefit> findByProjectId(java.util.UUID id);
}
