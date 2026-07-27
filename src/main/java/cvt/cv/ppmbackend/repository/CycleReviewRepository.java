package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.CycleReview; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface CycleReviewRepository extends JpaRepository<CycleReview,UUID>{@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from CycleReview r where r.id=:id") Optional<CycleReview> locked(@Param("id")UUID id);Optional<CycleReview> findByIdempotencyKey(String key);}
