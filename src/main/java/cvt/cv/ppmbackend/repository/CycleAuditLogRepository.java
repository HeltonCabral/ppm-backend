package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.CycleAuditLog; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface CycleAuditLogRepository extends JpaRepository<CycleAuditLog,UUID>{Page<CycleAuditLog> findByCycle_Id(UUID id,Pageable p);}
