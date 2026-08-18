package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.LookupValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LookupValueRepository extends JpaRepository<LookupValue, UUID> {
    List<LookupValue> findByCategoryOrderBySortOrderAsc(String category);

    List<LookupValue> findByCategoryAndActiveTrueOrderBySortOrderAsc(String category);

    Optional<LookupValue> findFirstByCategoryIgnoreCaseAndCodeIgnoreCaseAndActiveTrue(String category, String code);

    @Query("select l from LookupValue l where l.active = true and upper(l.category) in :categories "
            + "and (upper(l.code) = upper(:value) or upper(l.label) = upper(:value)) order by l.sortOrder")
    List<LookupValue> findDomainByCodeOrLabel(@Param("categories") List<String> categories,
            @Param("value") String value);
}
