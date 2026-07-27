package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.MacroResourceCreateRequest;
import cvt.cv.ppmbackend.entity.MacroResource;
import cvt.cv.ppmbackend.repository.MacroResourceRepository;
import org.springframework.stereotype.Service;

@Service
public class MacroResourceService extends AbstractCrudService<MacroResource, MacroResourceCreateRequest> {
    public MacroResourceService(MacroResourceRepository r) {
        super(r, "Recurso macro");
    }

    protected MacroResource newEntity() {
        return new MacroResource();
    }

    protected MacroResource apply(MacroResourceCreateRequest r, MacroResource e) {
        e.setName(r.name());
        e.setType(r.type());
        e.setTotalCapacity(r.totalCapacity());
        e.setCapacityUnit(r.capacityUnit());
        return e;
    }
}
