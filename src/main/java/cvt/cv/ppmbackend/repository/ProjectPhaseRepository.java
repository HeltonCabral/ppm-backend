package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectPhase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, UUID> {
    List<ProjectPhase> findByProjectIdOrderBySequenceAsc(UUID projectId);

    Optional<ProjectPhase> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectId(UUID projectId);

    List<ProjectPhase> findByProjectIdIn(java.util.Collection<UUID> projectIds);
}
