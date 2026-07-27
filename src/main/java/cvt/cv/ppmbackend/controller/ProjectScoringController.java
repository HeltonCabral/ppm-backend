package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectScoringCreateRequest;
import cvt.cv.ppmbackend.entity.ProjectScoring;
import cvt.cv.ppmbackend.service.ProjectScoringService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/project-scorings")
public class ProjectScoringController extends AbstractCrudController<ProjectScoring, ProjectScoringCreateRequest> {
    private final ProjectScoringService scorings;

    public ProjectScoringController(ProjectScoringService s) {
        super(s);
        scorings = s;
    }

    @PostMapping("/calculate")
    public Map<String, BigDecimal> calculate(@Valid @RequestBody ProjectScoringCreateRequest r) {
        return Map.of("finalScore", scorings.calculateFinalScore(r));
    }
}
