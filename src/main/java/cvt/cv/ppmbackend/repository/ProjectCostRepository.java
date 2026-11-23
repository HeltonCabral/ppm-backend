package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCostRepository extends JpaRepository<ProjectCost, UUID> {
    List<ProjectCost> findByProjectIdOrderByMonthAscCreatedAtAsc(UUID projectId);

    Optional<ProjectCost> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectId(UUID projectId);

    @Query("select coalesce(sum(c.actual), 0) from ProjectCost c where c.project.id = :projectId")
    BigDecimal sumActualByProjectId(UUID projectId);
}
