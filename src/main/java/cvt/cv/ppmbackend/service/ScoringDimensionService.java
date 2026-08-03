package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ScoringDimensionCreateRequest;
import cvt.cv.ppmbackend.entity.ScoringDimension;
import cvt.cv.ppmbackend.enums.ScoringImpactType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.ScoringDimensionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ScoringDimensionService extends AbstractCrudService<ScoringDimension, ScoringDimensionCreateRequest> {
    private final ScoringDimensionRepository dimensions;

    public ScoringDimensionService(ScoringDimensionRepository dimensions) {
        super(dimensions, "Dimensão de scoring");
        this.dimensions = dimensions;
    }

    @Override
    protected ScoringDimension newEntity() {
        return new ScoringDimension();
    }

    @Override
    protected ScoringDimension apply(ScoringDimensionCreateRequest request, ScoringDimension entity) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (entity.getId() == null) {
            dimensions.findByCodeIgnoreCase(code).ifPresent(existing -> {
                throw new BadRequestException("Já existe dimensão de scoring com code: " + code);
            });
        } else {
            dimensions.findByCodeIgnoreCase(code)
                    .filter(existing -> !existing.getId().equals(entity.getId()))
                    .ifPresent(existing -> {
                        throw new BadRequestException("Já existe dimensão de scoring com code: " + code);
                    });
        }

        entity.setCode(code);
        entity.setLabel(request.label());
        entity.setWeight(request.weight());
        entity.setImpactType(request.impactType() == null ? ScoringImpactType.BENEFIT : request.impactType());
        entity.setActive(request.active() == null ? Boolean.TRUE : request.active());
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoringDimension> findAll() {
        return dimensions.findAllWithCriteriaOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<ScoringDimension> findActive() {
        return dimensions.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public ScoringDimension requireActive(UUID id) {
        ScoringDimension dimension = findById(id);
        if (!Boolean.TRUE.equals(dimension.getActive())) {
            throw new BadRequestException("Dimensão de scoring inativa: " + id);
        }
        return dimension;
    }

    public ScoringDimension deactivate(UUID id) {
        ScoringDimension dimension = findById(id);
        dimension.setActive(false);
        return dimensions.save(dimension);
    }

    public ScoringDimension activate(UUID id) {
        ScoringDimension dimension = findById(id);
        dimension.setActive(true);
        return dimensions.save(dimension);
    }
}
