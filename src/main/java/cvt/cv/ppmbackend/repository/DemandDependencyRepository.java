package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandDependencyRepository extends JpaRepository<DemandDependency, UUID> {
    List<DemandDependency> findByDemand_IdOrderByCreatedAtAsc(UUID demandId);
    Optional<DemandDependency> findByIdAndDemand_Id(UUID id, UUID demandId);
    boolean existsByDemand_IdAndDependsOnDemand_Id(UUID demandId, UUID dependsOnDemandId);
    long countByDemand_Id(UUID demandId);

    @Query("select dependency.dependsOnDemand.id from DemandDependency dependency "
            + "where dependency.demand.id in :demandIds")
    List<UUID> findTargetIdsByDemandIds(@Param("demandIds") Collection<UUID> demandIds);
}
