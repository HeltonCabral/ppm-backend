package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectRisk;
import cvt.cv.ppmbackend.enums.ProjectRiskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRiskRepository extends JpaRepository<ProjectRisk, UUID> {
    List<ProjectRisk> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<ProjectRisk> findByIdAndProjectId(UUID id, UUID projectId);

    List<ProjectRisk> findByProjectIdAndStatusIn(UUID projectId, java.util.Collection<ProjectRiskStatus> statuses);

    List<ProjectRisk> findByStatusIn(java.util.Collection<ProjectRiskStatus> statuses);
}
