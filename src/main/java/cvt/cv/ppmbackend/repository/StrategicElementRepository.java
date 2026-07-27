package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.StrategicElement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StrategicElementRepository extends JpaRepository<StrategicElement, UUID> {
	List<StrategicElement> findByStrategicPillar_Id(UUID id);
}
