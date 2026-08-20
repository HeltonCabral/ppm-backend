package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    List<Profile> findAllByOrderByNameAsc();
    List<Profile> findByActiveTrueOrderByNameAsc();
    Optional<Profile> findByNameIgnoreCase(String name);
    List<Profile> findByDirectionCodeIgnoreCaseOrderByNameAsc(String directionCode);

    @Query("select p from Profile p where lower(p.directionCode) in :directionCodes order by p.name asc")
    List<Profile> findByDirectionCodeInIgnoreCase(@Param("directionCodes") List<String> directionCodesLowerCase);
}
