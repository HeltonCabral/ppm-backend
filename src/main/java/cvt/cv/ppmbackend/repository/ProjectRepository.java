package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface ProjectRepository extends JpaRepository<Project,java.util.UUID>, JpaSpecificationExecutor<Project> {
 List<Project> findByStatus(ProjectStatus status); List<Project> findByDomain_Id(java.util.UUID domainId);
 List<Project> findByProgram_Id(java.util.UUID programId);
 List<Project> findByOperationalPlan_Id(java.util.UUID operationalPlanId);

 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from Project p where p.status not in (cvt.cv.ppmbackend.enums.ProjectStatus.COMPLETED, cvt.cv.ppmbackend.enums.ProjectStatus.CANCELLED) order by p.executionRank asc nulls last, p.createdAt asc")
 List<Project> findActiveForRanking();
}
