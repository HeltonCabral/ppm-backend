package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectProfileRepository extends JpaRepository<ProjectProfile, UUID> {
    List<ProjectProfile> findByProjectIdOrderByProfileNameAsc(UUID projectId);

    boolean existsByProjectIdAndProfile_Id(UUID projectId, UUID profileId);

    /** Perfis previstos dos projetos com os estados indicados (os que ocupam pessoas). */
    @Query("select pp from ProjectProfile pp join fetch pp.project p where p.status in :statuses")
    List<ProjectProfile> findInProjectsWithStatus(@Param("statuses") Collection<ProjectStatus> statuses);
}
