package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.StrategicPillarCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicPillar;
import cvt.cv.ppmbackend.service.StrategicPillarService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/strategic-pillars")
public class StrategicPillarController extends AbstractCrudController<StrategicPillar, StrategicPillarCreateRequest> {
    public StrategicPillarController(StrategicPillarService s) {
        super(s);
    }
}
