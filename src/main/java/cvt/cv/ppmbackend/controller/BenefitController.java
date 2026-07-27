package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.BenefitCreateRequest;
import cvt.cv.ppmbackend.entity.Benefit;
import cvt.cv.ppmbackend.service.BenefitService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/benefits")
public class BenefitController extends AbstractCrudController<Benefit, BenefitCreateRequest> {
    private final BenefitService benefits;

    public BenefitController(BenefitService s) {
        super(s);
        benefits = s;
    }

    @GetMapping("/project/{id}")
    public List<Benefit> byProject(@PathVariable UUID id) {
        return benefits.findByProject(id);
    }
}
