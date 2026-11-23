package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.PhaseDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhaseDefinitionRepository extends JpaRepository<PhaseDefinition, UUID> {

    List<PhaseDefinition> findByPhaseModelIdOrderBySequenceAsc(UUID phaseModelId);

    boolean existsByPhaseModelIdAndCodeIgnoreCase(UUID phaseModelId, String code);

    boolean existsByPhaseModelIdAndCodeIgnoreCaseAndIdNot(UUID phaseModelId, String code, UUID id);
}
