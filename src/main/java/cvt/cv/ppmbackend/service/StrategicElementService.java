package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicElementCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicElement;
import cvt.cv.ppmbackend.repository.StrategicElementRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class StrategicElementService
        extends AbstractCrudService<StrategicElement, StrategicElementCreateRequest> {
    private final StrategicElementRepository elements;
    private final StrategicPillarService pillars;

    public StrategicElementService(StrategicElementRepository r, StrategicPillarService p) {
        super(r, "Elemento estratégico");
        elements = r;
        pillars = p;
    }

    protected StrategicElement newEntity() {
        return new StrategicElement();
    }

    protected StrategicElement apply(StrategicElementCreateRequest r, StrategicElement e) {
        e.setName(r.name());
        e.setDescription(r.description());
        e.setIcon(r.icon());
        e.setStrategicPillar(pillars.findById(r.strategicPillarId()));
        return e;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<StrategicElement> findByPillar(UUID id) {
        pillars.findById(id);
        return elements.findByStrategicPillar_Id(id);
    }
}
