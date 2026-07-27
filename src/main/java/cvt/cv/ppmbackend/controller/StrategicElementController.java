package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.StrategicElementCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicElement;
import cvt.cv.ppmbackend.service.StrategicElementService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/strategic-elements")
public class StrategicElementController
        extends AbstractCrudController<StrategicElement, StrategicElementCreateRequest> {
    private final StrategicElementService elements;

    public StrategicElementController(StrategicElementService s) {
        super(s);
        elements = s;
    }

    @GetMapping("/pillar/{id}")
    public List<StrategicElement> byPillar(@PathVariable UUID id) {
        return elements.findByPillar(id);
    }
}
