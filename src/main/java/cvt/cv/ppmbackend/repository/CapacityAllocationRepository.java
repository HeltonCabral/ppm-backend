package cvt.cv.ppmbackend.repository;
import cvt.cv.ppmbackend.entity.CapacityAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.YearMonth;
import java.util.List;
public interface CapacityAllocationRepository extends JpaRepository<CapacityAllocation,java.util.UUID>{
 List<CapacityAllocation> findByProjectId(java.util.UUID id);List<CapacityAllocation> findByMacroResourceId(java.util.UUID id);List<CapacityAllocation> findByMacroResourceIdAndPeriod(java.util.UUID id,YearMonth period);
}
