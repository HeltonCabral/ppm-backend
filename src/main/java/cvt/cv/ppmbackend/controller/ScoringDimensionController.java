package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ScoringDimensionCreateRequest;
import cvt.cv.ppmbackend.entity.ScoringDimension;
import cvt.cv.ppmbackend.service.ScoringDimensionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/scoring-dimensions")
public class ScoringDimensionController extends AbstractCrudController<ScoringDimension, ScoringDimensionCreateRequest> {
    private final ScoringDimensionService dimensions;

    public ScoringDimensionController(ScoringDimensionService dimensions) {
        super(dimensions);
        this.dimensions = dimensions;
    }

    @GetMapping("/active")
    public List<ScoringDimension> findActive() {
        return dimensions.findActive();
    }

    @PatchMapping("/{id}/deactivate")
    public ScoringDimension deactivate(@PathVariable UUID id) {
        return dimensions.deactivate(id);
    }

    @PatchMapping("/{id}/activate")
    public ScoringDimension activate(@PathVariable UUID id) {
        return dimensions.activate(id);
    }
}
