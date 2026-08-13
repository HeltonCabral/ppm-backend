package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProjectRepository extends JpaRepository<Project,java.util.UUID>{
 List<Project> findByStatus(ProjectStatus status); List<Project> findByDomain_Id(java.util.UUID domainId);
 List<Project> findByProgram_Id(java.util.UUID programId); List<Project> findByRiskStatus(ExecutiveStatus riskStatus);
 List<Project> findByOperationalPlan_Id(java.util.UUID operationalPlanId);
}
