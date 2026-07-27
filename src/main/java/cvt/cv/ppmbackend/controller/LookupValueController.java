package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.LookupValueCreateRequest;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.service.LookupValueService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/lookup-values")
public class LookupValueController extends AbstractCrudController<LookupValue, LookupValueCreateRequest> {
    private final LookupValueService lookupService;

    public LookupValueController(LookupValueService s) {
        super(s);
        this.lookupService = s;
    }

    @GetMapping("/category/{category}")
    public List<LookupValue> findByCategory(@PathVariable String category) {
        return lookupService.findByCategory(category);
    }

    @PatchMapping("/{id}/deactivate")
    public LookupValue deactivate(@PathVariable UUID id) {
        return lookupService.deactivate(id);
    }

    @PatchMapping("/{id}/activate")
    public LookupValue activate(@PathVariable UUID id) {
        return lookupService.activate(id);
    }
}
