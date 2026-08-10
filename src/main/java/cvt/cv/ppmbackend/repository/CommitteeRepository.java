package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommitteeRepository extends JpaRepository<Committee, UUID> {
    List<Committee> findAllByOrderByNameAsc();

    List<Committee> findByStatusOrderByNameAsc(CommitteeStatus status);

    boolean existsByNameKey(String nameKey);

    boolean existsByNameKeyAndIdNot(String nameKey, UUID id);
}
