package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectTeamMemberRepository extends JpaRepository<ProjectTeamMember, UUID> {
    List<ProjectTeamMember> findByProjectId(UUID projectId);
}
