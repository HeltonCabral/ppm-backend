package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectTeamMemberRepository extends JpaRepository<ProjectTeamMember, UUID> {
    List<ProjectTeamMember> findByProjectId(UUID projectId);

    List<ProjectTeamMember> findByProjectProfileId(UUID projectProfileId);

    /** Membros dos projetos com os estados indicados (os que ocupam pessoas). */
    @Query("select m from ProjectTeamMember m join fetch m.project p where p.status in :statuses")
    List<ProjectTeamMember> findInProjectsWithStatus(@Param("statuses") Collection<ProjectStatus> statuses);

    /** Alocações de uma pessoa (INTERNAL, mesmo code sem distinguir maiúsculas) nesses projetos. */
    @Query("select m from ProjectTeamMember m join fetch m.project p where p.status in :statuses "
            + "and m.type = cvt.cv.ppmbackend.enums.MemberType.INTERNAL and lower(m.code) = lower(:code)")
    List<ProjectTeamMember> findPersonAssignments(@Param("code") String code,
            @Param("statuses") Collection<ProjectStatus> statuses);
}
