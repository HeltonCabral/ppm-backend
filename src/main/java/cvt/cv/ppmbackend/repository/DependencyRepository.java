package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.Dependency;
import cvt.cv.ppmbackend.enums.DependencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DependencyRepository extends JpaRepository<Dependency,java.util.UUID>{
 List<Dependency> findBySourceProjectId(java.util.UUID id); List<Dependency> findByStatus(DependencyStatus status);
}
