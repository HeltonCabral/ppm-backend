package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementInput;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.service.DemandProfileRequirementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demands/{demandId}/profile-requirements")
public class DemandProfileRequirementController {

    private final DemandProfileRequirementService service;
    private final DemandRepository demands;

    public DemandProfileRequirementController(DemandProfileRequirementService service, DemandRepository demands) {
        this.service = service;
        this.demands = demands;
    }

    @GetMapping
    public List<DemandProfileRequirementResponse> list(@PathVariable UUID demandId) {
        requireDemand(demandId);
        return service.list(demandId);
    }

    @PostMapping
    public ResponseEntity<DemandProfileRequirementResponse> add(@PathVariable UUID demandId,
            @Valid @RequestBody DemandProfileRequirementInput request) {
        Demand demand = requireDemand(demandId);
        DemandProfileRequirementResponse saved = service.addRequirement(demand, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{requirementId}")
                .buildAndExpand(saved.id())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{requirementId}")
    public DemandProfileRequirementResponse update(@PathVariable UUID demandId, @PathVariable UUID requirementId,
            @Valid @RequestBody DemandProfileRequirementInput request) {
        Demand demand = requireDemand(demandId);
        return service.updateRequirement(demand, requirementId, request);
    }

    @DeleteMapping("/{requirementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID demandId, @PathVariable UUID requirementId) {
        Demand demand = requireDemand(demandId);
        service.removeRequirement(demand, requirementId);
    }

    private Demand requireDemand(UUID demandId) {
        return demands.findByIdAndDeletedAtIsNull(demandId)
                .orElseThrow(() -> new ResourceNotFoundException("Demanda não encontrada: " + demandId));
    }
}
