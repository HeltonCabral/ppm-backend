package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface DemandRepository extends JpaRepository<Demand, UUID>, JpaSpecificationExecutor<Demand> {
    Optional<Demand> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCode(String code);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Demand d " +
           "WHERE d.suggestedCommittee.id = :committeeId OR d.responsibleCommittee.id = :committeeId")
    boolean existsBySuggestedCommitteeIdOrResponsibleCommitteeId(@Param("committeeId") UUID committeeId,
            @Param("committeeId") UUID responsibleCommitteeId);
}
