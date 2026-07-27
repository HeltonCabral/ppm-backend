package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.LookupValueCreateRequest;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LookupValueService extends AbstractCrudService<LookupValue, LookupValueCreateRequest> {
    private final LookupValueRepository lookups;

    public LookupValueService(LookupValueRepository r) {
        super(r, "Lookup value");
        lookups = r;
    }

    protected LookupValue newEntity() {
        return new LookupValue();
    }

    protected LookupValue apply(LookupValueCreateRequest r, LookupValue e) {
        e.setCategory(r.category().toUpperCase());
        e.setCode(r.code().toUpperCase());
        e.setLabel(r.label());
        e.setSortOrder(r.sortOrder());
        if (e.getActive() == null)
            e.setActive(true);
        return e;
    }

    @Transactional(readOnly = true)
    public List<LookupValue> findByCategory(String category) {
        return lookups.findByCategoryAndActiveTrueOrderBySortOrderAsc(category.toUpperCase());
    }

    @Transactional(readOnly = true)
    public boolean existsCode(String category, String code) {
        if (category == null || code == null)
            return false;
        return lookups.findFirstByCategoryIgnoreCaseAndCodeIgnoreCaseAndActiveTrue(category, code).isPresent();
    }

    @Transactional(readOnly = true)
    public LookupValue requireCode(String category, String code) {
        return lookups.findFirstByCategoryIgnoreCaseAndCodeIgnoreCaseAndActiveTrue(category, code)
                .orElseThrow(() -> new BadRequestException(
                        "Lookup inválido para categoria " + category + " e código " + code));
    }

    @Transactional(readOnly = true)
    public LookupValue requireActiveInCategory(UUID id, String category) {
        LookupValue value = findById(id);
        if (!category.equalsIgnoreCase(value.getCategory()) || !Boolean.TRUE.equals(value.getActive())) {
            throw new BadRequestException("Lookup inválido para categoria " + category + ": " + id);
        }
        return value;
    }

    public LookupValue deactivate(java.util.UUID id) {
        LookupValue lv = findById(id);
        lv.setActive(false);
        return lookups.save(lv);
    }

    public LookupValue activate(java.util.UUID id) {
        LookupValue lv = findById(id);
        lv.setActive(true);
        return lookups.save(lv);
    }
}
