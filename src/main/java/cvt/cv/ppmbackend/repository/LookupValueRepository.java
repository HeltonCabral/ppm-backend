package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.LookupValue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LookupValueRepository extends JpaRepository<LookupValue, UUID> {
    List<LookupValue> findByCategoryOrderBySortOrderAsc(String category);

    List<LookupValue> findByCategoryAndActiveTrueOrderBySortOrderAsc(String category);

    Optional<LookupValue> findFirstByCategoryIgnoreCaseAndCodeIgnoreCaseAndActiveTrue(String category, String code);
}
