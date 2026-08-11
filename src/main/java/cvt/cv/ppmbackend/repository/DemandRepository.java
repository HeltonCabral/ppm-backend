package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandRepository extends JpaRepository<Demand, UUID>, JpaSpecificationExecutor<Demand> {
    Optional<Demand> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCode(String code);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Demand d " +
           "WHERE d.responsibleCommittee.id = :committeeId")
    boolean existsByResponsibleCommitteeId(@Param("committeeId") UUID committeeId);

       List<Demand> findByResponsibleCommittee_IdAndStatusAndDeletedAtIsNullOrderByPreScoreDescCreatedAtDesc(
            UUID committeeId,
            String status);

           List<Demand> findByResponsibleCommittee_IdInAndStatusAndDeletedAtIsNullOrderByPreScoreDescCreatedAtDesc(
                  List<UUID> committeeIds,
                  String status);
}
