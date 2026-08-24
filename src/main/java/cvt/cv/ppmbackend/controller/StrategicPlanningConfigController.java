package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.StrategicPlanningConfigDtos.Response;
import cvt.cv.ppmbackend.dto.StrategicPlanningConfigDtos.UpdateRequest;
import cvt.cv.ppmbackend.service.StrategicPlanningConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/strategic-planning/config")
public class StrategicPlanningConfigController {
    private final StrategicPlanningConfigService configs;

    public StrategicPlanningConfigController(StrategicPlanningConfigService configs) {
        this.configs = configs;
    }

    @GetMapping
    public Response get() {
        return Response.from(configs.getActive());
    }

    @PutMapping
    public Response update(@Valid @RequestBody UpdateRequest request) {
        return Response.from(configs.update(request));
    }
}
