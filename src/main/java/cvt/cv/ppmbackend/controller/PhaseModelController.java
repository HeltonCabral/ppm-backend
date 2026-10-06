package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.PhaseModelDtos.ActiveToggleRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelResponse;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseResponse;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ReorderRequest;
import cvt.cv.ppmbackend.service.PhaseModelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/phase-models")
public class PhaseModelController {

    private final PhaseModelService service;

    public PhaseModelController(PhaseModelService service) {
        this.service = service;
    }

    @GetMapping
    public List<ModelResponse> list(@RequestParam(name = "activeOnly", required = false) Boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    public ModelResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ModelResponse> create(@Valid @RequestBody ModelRequest request) {
        ModelResponse saved = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.id())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    public ModelResponse update(@PathVariable UUID id, @Valid @RequestBody ModelRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public ModelResponse setActive(@PathVariable UUID id, @Valid @RequestBody ActiveToggleRequest request) {
        return service.setActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/phases")
    public ResponseEntity<PhaseResponse> addPhase(@PathVariable UUID id, @Valid @RequestBody PhaseRequest request) {
        PhaseResponse saved = service.addPhase(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{phaseId}")
                .buildAndExpand(saved.id())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}/phases/{phaseId}")
    public PhaseResponse updatePhase(@PathVariable UUID id, @PathVariable UUID phaseId,
            @Valid @RequestBody PhaseRequest request) {
        return service.updatePhase(id, phaseId, request);
    }

    @PatchMapping("/{id}/phases/{phaseId}/active")
    public PhaseResponse setPhaseActive(@PathVariable UUID id, @PathVariable UUID phaseId,
            @Valid @RequestBody ActiveToggleRequest request) {
        return service.setPhaseActive(id, phaseId, request.active());
    }

    @DeleteMapping("/{id}/phases/{phaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhase(@PathVariable UUID id, @PathVariable UUID phaseId) {
        service.deletePhase(id, phaseId);
    }

    @PutMapping("/{id}/phases/reorder")
    public List<PhaseResponse> reorder(@PathVariable UUID id, @Valid @RequestBody ReorderRequest request) {
        return service.reorder(id, request);
    }
}
