package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
	List<Program> findByStrategicObjectives_StrategicPlan_Id(UUID id);
}
