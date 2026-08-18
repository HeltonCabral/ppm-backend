package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    List<Profile> findAllByOrderByNameAsc();
    List<Profile> findByActiveTrueOrderByNameAsc();
    Optional<Profile> findByNameIgnoreCase(String name);
}
