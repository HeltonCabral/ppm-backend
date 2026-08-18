package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectExecutionRepository extends JpaRepository<ProjectExecution, UUID> {
    Optional<ProjectExecution> findByProject_Id(UUID projectId);
}
