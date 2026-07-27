package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ScoringCriterionCreateRequest;
import cvt.cv.ppmbackend.entity.ScoringCriterion;
import cvt.cv.ppmbackend.service.ScoringCriterionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/scoring-criteria")
public class ScoringCriterionController extends AbstractCrudController<ScoringCriterion, ScoringCriterionCreateRequest> {
    private final ScoringCriterionService service;

    public ScoringCriterionController(ScoringCriterionService service) {
        super(service);
        this.service = service;
    }

    @GetMapping("/active")
    public List<ScoringCriterion> findActive() {
        return service.findActive();
    }

    @GetMapping("/by-dimension")
    public List<ScoringCriterion> findByDimension(@RequestParam UUID dimensionId) {
        return service.findByDimension(dimensionId);
    }

    @PatchMapping("/{id}/deactivate")
    public ScoringCriterion deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PatchMapping("/{id}/activate")
    public ScoringCriterion activate(@PathVariable UUID id) {
        return service.activate(id);
    }
}
