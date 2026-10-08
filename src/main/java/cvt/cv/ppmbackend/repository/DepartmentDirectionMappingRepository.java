package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DepartmentDirectionMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentDirectionMappingRepository extends JpaRepository<DepartmentDirectionMapping, UUID> {

    List<DepartmentDirectionMapping> findAllByOrderByDepartmentKeyAsc();

    Optional<DepartmentDirectionMapping> findByDepartmentKey(String departmentKey);
}
