package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandProfileRequirementRepository extends JpaRepository<DemandProfileRequirement, UUID> {

    List<DemandProfileRequirement> findByDemand_IdOrderByProfile_NameAsc(UUID demandId);

    List<DemandProfileRequirement> findByDemand_IdIn(Collection<UUID> demandIds);

    Optional<DemandProfileRequirement> findByDemand_IdAndProfile_Id(UUID demandId, UUID profileId);

    boolean existsByProfile_Id(UUID profileId);

    void deleteByDemand_Id(UUID demandId);
}
