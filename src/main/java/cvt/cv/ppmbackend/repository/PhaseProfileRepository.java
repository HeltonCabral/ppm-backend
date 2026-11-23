package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.PhaseProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PhaseProfileRepository extends JpaRepository<PhaseProfile, UUID> {

    List<PhaseProfile> findByPhaseDefinitionIdOrderByProfile_NameAsc(UUID phaseDefinitionId);

    boolean existsByPhaseDefinitionIdAndProfile_Id(UUID phaseDefinitionId, UUID profileId);

    boolean existsByProfile_Id(UUID profileId);

    @Query("select pp from PhaseProfile pp where pp.phaseDefinition.phaseModel.id = :modelId")
    List<PhaseProfile> findByPhaseModelId(@Param("modelId") UUID modelId);
}
