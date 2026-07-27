package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectScoringCreateRequest;
import cvt.cv.ppmbackend.entity.ProjectScoring;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.ProjectScoringRepository;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.Objects;

@Service
public class ProjectScoringService extends AbstractCrudService<ProjectScoring, ProjectScoringCreateRequest> {
    private final ProjectScoringRepository scorings;
    private final ProjectService projects;

    public ProjectScoringService(ProjectScoringRepository r, ProjectService p) {
        super(r, "Scoring");
        scorings = r;
        projects = p;
    }

    protected ProjectScoring newEntity() {
        return new ProjectScoring();
    }

    protected ProjectScoring apply(ProjectScoringCreateRequest r, ProjectScoring e) {
        scorings.findByProjectId(r.projectId()).filter(f -> !Objects.equals(f.getId(), e.getId())).ifPresent(f -> {
            throw new BadRequestException("O projeto já possui scoring");
        });
        e.setProject(projects.findById(r.projectId()));
        e.setStrategicAlignment(r.strategicAlignment());
        e.setRoi(r.roi());
        e.setUrgency(r.urgency());
        e.setTechnicalComplexity(r.technicalComplexity());
        e.setResourceAvailability(r.resourceAvailability());
        e.setEstimatedDuration(r.estimatedDuration());
        e.setTechnologyRisk(r.technologyRisk());
        e.setDependencyRisk(r.dependencyRisk());
        e.setAdoptionRisk(r.adoptionRisk());
        e.setFinalScore(calculateFinalScore(r));
        return e;
    }

    public BigDecimal calculateFinalScore(ProjectScoringCreateRequest r) {
        BigDecimal v = avg(r.strategicAlignment(), r.roi(), r.urgency()),
                e = avg(r.technicalComplexity(), r.resourceAvailability(), r.estimatedDuration()),
                k = avg(r.technologyRisk(), r.dependencyRisk(), r.adoptionRisk());
        return v.multiply(new BigDecimal("0.50")).subtract(e.multiply(new BigDecimal("0.30")))
                .subtract(k.multiply(new BigDecimal("0.20"))).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal avg(Integer a, Integer b, Integer c) {
        return BigDecimal.valueOf(a + b + c).divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
    }
}
