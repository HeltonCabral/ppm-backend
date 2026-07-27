package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DemandCounterRepository extends JpaRepository<DemandCounter, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DemandCounter c where c.counterKey = :key")
    Optional<DemandCounter> lockByKey(String key);
}
