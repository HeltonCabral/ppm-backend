package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectChangeRequest;
import cvt.cv.ppmbackend.enums.ChangeRequestKind;
import cvt.cv.ppmbackend.enums.ChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectChangeRequestRepository extends JpaRepository<ProjectChangeRequest, UUID> {
    List<ProjectChangeRequest> findByProjectIdOrderByRequestedAtDesc(UUID projectId);

    Optional<ProjectChangeRequest> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndKindAndStatus(UUID projectId, ChangeRequestKind kind, ChangeRequestStatus status);
}
