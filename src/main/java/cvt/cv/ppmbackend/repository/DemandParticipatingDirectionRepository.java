package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandParticipatingDirectionRepository extends JpaRepository<DemandParticipatingDirection, UUID> {
    List<DemandParticipatingDirection> findByDemandIdOrderByCreatedAtAsc(UUID demandId);
    void deleteByDemandId(UUID demandId);
}
