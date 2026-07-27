package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.MacroResourceCreateRequest;
import cvt.cv.ppmbackend.entity.MacroResource;
import cvt.cv.ppmbackend.service.MacroResourceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/macro-resources")
public class MacroResourceController extends AbstractCrudController<MacroResource, MacroResourceCreateRequest> {
    public MacroResourceController(MacroResourceService s) {
        super(s);
    }
}
