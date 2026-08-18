package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.CriterionConfigRequest;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.CriterionConfigResponse;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.LevelConfigRequest;
import cvt.cv.ppmbackend.dto.ComplexityConfigDtos.LevelConfigResponse;
import cvt.cv.ppmbackend.service.DemandComplexityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/complexity")
public class AdminComplexityController {
    private final DemandComplexityService complexity;

    public AdminComplexityController(DemandComplexityService complexity) {
        this.complexity = complexity;
    }

    @GetMapping("/criteria")
    public List<CriterionConfigResponse> getCriteria() {
        return complexity.getCriteria();
    }

    @PutMapping("/criteria")
    public List<CriterionConfigResponse> updateCriteria(
            @RequestBody @NotEmpty List<@Valid CriterionConfigRequest> request) {
        return complexity.updateCriteria(request);
    }

    @GetMapping("/levels")
    public List<LevelConfigResponse> getLevels() {
        return complexity.getLevels();
    }

    @PutMapping("/levels")
    public List<LevelConfigResponse> updateLevels(
            @RequestBody @NotEmpty List<@Valid LevelConfigRequest> request) {
        return complexity.updateLevels(request);
    }
}
