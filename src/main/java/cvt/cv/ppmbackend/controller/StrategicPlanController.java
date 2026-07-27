package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.StrategicPlanCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.service.StrategicPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/strategic-plans")
public class StrategicPlanController extends AbstractCrudController<StrategicPlan, StrategicPlanCreateRequest> {
    private final StrategicPlanService plans;

    public StrategicPlanController(StrategicPlanService s) {
        super(s);
        plans = s;
    }

    @Override
    @GetMapping
    public List<StrategicPlan> findAll() {
        return plans.findAllOrdered();
    }

    @PostMapping("/{id}/activate")
    public StrategicPlan activate(@PathVariable UUID id) {
        return plans.activate(id);
    }

    @PostMapping("/{id}/rollover")
    public ResponseEntity<StrategicPlan> rollover(@PathVariable UUID id) {
        StrategicPlan saved = plans.rollover(id);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().replacePath("/api/strategic-plans/{id}")
                .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(uri).body(saved);
    }
}
