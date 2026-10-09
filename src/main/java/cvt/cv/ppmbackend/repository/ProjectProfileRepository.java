package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectProfileRepository extends JpaRepository<ProjectProfile, UUID> {
    List<ProjectProfile> findByProjectIdOrderByProfileNameAsc(UUID projectId);

    boolean existsByProjectIdAndProfile_Id(UUID projectId, UUID profileId);
}
