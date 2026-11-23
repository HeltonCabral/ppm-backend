package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectEventRepository extends JpaRepository<ProjectEvent, UUID> {
    List<ProjectEvent> findByProjectIdOrderByAtDesc(UUID projectId);

    org.springframework.data.domain.Page<ProjectEvent> findByProjectIdOrderByAtDesc(UUID projectId,
            org.springframework.data.domain.Pageable pageable);
}
