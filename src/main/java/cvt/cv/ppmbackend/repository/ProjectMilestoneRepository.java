package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectMilestone;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {
    List<ProjectMilestone> findByProjectIdOrderByDueDateAscCreatedAtAsc(UUID projectId);

    Optional<ProjectMilestone> findByIdAndProjectId(UUID id, UUID projectId);
}
