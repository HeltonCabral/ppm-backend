package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandParticipatingDirectionArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandParticipatingDirectionAreaRepository
        extends JpaRepository<DemandParticipatingDirectionArea, UUID> {
    List<DemandParticipatingDirectionArea> findByParticipatingDirectionIdOrderByCreatedAtAsc(UUID participatingDirectionId);
    void deleteByParticipatingDirectionId(UUID participatingDirectionId);
    void deleteByParticipatingDirection_Demand_Id(UUID demandId);
}
