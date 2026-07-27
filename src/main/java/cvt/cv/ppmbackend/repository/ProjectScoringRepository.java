package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectScoring;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProjectScoringRepository extends JpaRepository<ProjectScoring, java.util.UUID> {
    Optional<ProjectScoring> findByProjectId(java.util.UUID id);
}
