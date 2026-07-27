package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.DemandAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandAttachmentRepository extends JpaRepository<DemandAttachment, UUID> {
    List<DemandAttachment> findByDemandIdOrderByCreatedAtDesc(UUID demandId);

    Optional<DemandAttachment> findByIdAndDemandId(UUID id, UUID demandId);
}
