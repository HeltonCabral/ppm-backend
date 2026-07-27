package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ScoringCriterionCreateRequest;
import cvt.cv.ppmbackend.entity.ScoringDimension;
import cvt.cv.ppmbackend.entity.ScoringCriterion;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.ScoringDimensionRepository;
import cvt.cv.ppmbackend.repository.ScoringCriterionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ScoringCriterionService extends AbstractCrudService<ScoringCriterion, ScoringCriterionCreateRequest> {
    private final ScoringCriterionRepository criteria;
    private final ScoringDimensionRepository dimensions;

    public ScoringCriterionService(ScoringCriterionRepository criteria, ScoringDimensionRepository dimensions) {
        super(criteria, "Critério de scoring");
        this.criteria = criteria;
        this.dimensions = dimensions;
    }

    @Override
    protected ScoringCriterion newEntity() {
        return new ScoringCriterion();
    }

    @Override
    protected ScoringCriterion apply(ScoringCriterionCreateRequest request, ScoringCriterion entity) {
        if (request.minScore().compareTo(request.maxScore()) > 0) {
            throw new BadRequestException("minScore não pode ser maior que maxScore");
        }
        ScoringDimension dimension = dimensions.findById(request.dimensionId())
                .orElseThrow(() -> new BadRequestException("Dimensão de scoring não encontrada: " + request.dimensionId()));
        if (!Boolean.TRUE.equals(dimension.getActive())) {
            throw new BadRequestException("Dimensão de scoring inativa: " + request.dimensionId());
        }
        entity.setLabel(request.label());
        entity.setDimension(dimension);
        entity.setMinScore(request.minScore());
        entity.setMaxScore(request.maxScore());
        entity.setOrderIndex(request.orderIndex());
        entity.setActive(request.active() == null ? Boolean.TRUE : request.active());
        return entity;
    }

    @Transactional(readOnly = true)
    public List<ScoringCriterion> findActive() {
        return criteria.findByActiveTrueOrderByOrderIndexAsc();
    }

    @Transactional(readOnly = true)
    public List<ScoringCriterion> findByDimension(UUID dimensionId) {
        return criteria.findByDimensionIdAndActiveTrueOrderByOrderIndexAsc(dimensionId);
    }

    @Transactional(readOnly = true)
    public ScoringCriterion requireActive(UUID id) {
        ScoringCriterion criterion = findById(id);
        if (!Boolean.TRUE.equals(criterion.getActive())) {
            throw new BadRequestException("Critério inativo: " + id);
        }
        return criterion;
    }

    public ScoringCriterion deactivate(UUID id) {
        ScoringCriterion criterion = findById(id);
        criterion.setActive(false);
        return criteria.save(criterion);
    }

    public ScoringCriterion activate(UUID id) {
        ScoringCriterion criterion = findById(id);
        criterion.setActive(true);
        return criteria.save(criterion);
    }
}
