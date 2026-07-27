package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StrategicObjectiveRepository extends JpaRepository<StrategicObjective, UUID> {
	List<StrategicObjective> findByStrategicPlan_Id(UUID id);

	List<StrategicObjective> findByStrategicElement_Id(UUID id);
}
