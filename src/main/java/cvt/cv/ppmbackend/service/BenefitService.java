package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.BenefitCreateRequest;
import cvt.cv.ppmbackend.entity.Benefit;
import cvt.cv.ppmbackend.repository.BenefitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class BenefitService extends AbstractCrudService<Benefit, BenefitCreateRequest> {
    private final BenefitRepository benefits;
    private final ProjectService projects;

    public BenefitService(BenefitRepository r, ProjectService p) {
        super(r, "Benefício");
        benefits = r;
        projects = p;
    }

    protected Benefit newEntity() {
        return new Benefit();
    }

    protected Benefit apply(BenefitCreateRequest r, Benefit e) {
        e.setProject(projects.findById(r.projectId()));
        e.setBenefitType(r.benefitType());
        e.setExpectedValue(r.expectedValue());
        e.setRealizedValue(r.realizedValue());
        e.setTrackingDate(r.trackingDate());
        e.setNotes(r.notes());
        return e;
    }

    @Transactional(readOnly = true)
    public List<Benefit> findByProject(UUID id) {
        projects.findById(id);
        return benefits.findByProjectId(id);
    }
}
