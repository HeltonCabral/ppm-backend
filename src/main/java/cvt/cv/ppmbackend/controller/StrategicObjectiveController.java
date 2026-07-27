package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.StrategicObjectiveCreateRequest;
import cvt.cv.ppmbackend.dto.StrategicObjectiveResponse;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import cvt.cv.ppmbackend.service.StrategicObjectiveService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/strategic-objectives")
public class StrategicObjectiveController
        extends AbstractCrudController<StrategicObjective, StrategicObjectiveCreateRequest> {
    private final StrategicObjectiveService objectiveService;

    public StrategicObjectiveController(StrategicObjectiveService s) {
        super(s);
        this.objectiveService = s;
    }

    @Override
    public List<StrategicObjectiveResponse> findAll() {
        return objectiveService.findAll().stream().map(StrategicObjectiveResponse::from).toList();
    }

    @Override
    public StrategicObjectiveResponse findById(@PathVariable UUID id) {
        return StrategicObjectiveResponse.from(objectiveService.findById(id));
    }

    @GetMapping("/element/{id}")
    public List<StrategicObjectiveResponse> byElement(@PathVariable UUID id) {
        return objectiveService.findByElement(id).stream().map(StrategicObjectiveResponse::from).toList();
    }
}
