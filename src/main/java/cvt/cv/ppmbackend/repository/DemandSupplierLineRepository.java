package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandSupplierLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandSupplierLineRepository extends JpaRepository<DemandSupplierLine, UUID> {
    List<DemandSupplierLine> findByDemandIdOrderByDisplayOrderAsc(UUID demandId);

    void deleteByDemandId(UUID demandId);
}
