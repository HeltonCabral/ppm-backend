package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectBaselineRepository extends JpaRepository<ProjectBaseline, UUID> {
    List<ProjectBaseline> findByProjectIdOrderByVersionDesc(UUID projectId);
    Optional<ProjectBaseline> findFirstByProjectIdOrderByVersionDesc(UUID projectId);
    boolean existsByProjectId(UUID projectId);
}
