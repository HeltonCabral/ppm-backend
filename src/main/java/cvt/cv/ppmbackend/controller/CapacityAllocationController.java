package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.CapacityAllocationCreateRequest;
import cvt.cv.ppmbackend.entity.CapacityAllocation;
import cvt.cv.ppmbackend.service.CapacityAllocationService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/capacity-allocations")
public class CapacityAllocationController
        extends AbstractCrudController<CapacityAllocation, CapacityAllocationCreateRequest> {
    private final CapacityAllocationService allocations;

    public CapacityAllocationController(CapacityAllocationService s) {
        super(s);
        allocations = s;
    }

    @GetMapping("/project/{id}")
    public List<CapacityAllocation> byProject(@PathVariable UUID id) {
        return allocations.findByProject(id);
    }

    @GetMapping("/resource/{id}")
    public List<CapacityAllocation> byResource(@PathVariable UUID id) {
        return allocations.findByResource(id);
    }
}
   
