package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProjectDependencyRepository extends JpaRepository<ProjectDependency, UUID> {
    @Query("select d from ProjectDependency d where d.predecessor.id = :projectId or d.successor.id = :projectId order by d.createdAt desc")
    List<ProjectDependency> findByEitherProject(UUID projectId);
    List<ProjectDependency> findByPredecessorId(UUID predecessorId);
}
