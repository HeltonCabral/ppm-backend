package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.PhaseModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhaseModelRepository extends JpaRepository<PhaseModel, UUID> {

    @EntityGraph(attributePaths = "phases")
    List<PhaseModel> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "phases")
    List<PhaseModel> findAllByActiveOrderByNameAsc(boolean active);

    @EntityGraph(attributePaths = "phases")
    Optional<PhaseModel> findWithPhasesById(UUID id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
