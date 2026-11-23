package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectPhaseGate;
import cvt.cv.ppmbackend.enums.ProjectPhaseGateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPhaseGateRepository extends JpaRepository<ProjectPhaseGate, UUID> {
    List<ProjectPhaseGate> findByPhase_Project_IdOrderByPhase_SequenceAsc(UUID projectId);
    Optional<ProjectPhaseGate> findByPhaseId(UUID phaseId);
    List<ProjectPhaseGate> findByStatusIn(Collection<ProjectPhaseGateStatus> statuses);
}
