package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectClosure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectClosureRepository extends JpaRepository<ProjectClosure, UUID> {
    Optional<ProjectClosure> findByProjectId(UUID projectId);
}
