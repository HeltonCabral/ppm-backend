package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.DemandDependencyDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.DemandDependencyDtos.Response;
import cvt.cv.ppmbackend.service.DemandDependencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demands/{demandId}/dependencies")
public class DemandDependencyController {
    private final DemandDependencyService dependencies;

    public DemandDependencyController(DemandDependencyService dependencies) {
        this.dependencies = dependencies;
    }

    @GetMapping
    public List<Response> list(@PathVariable UUID demandId) {
        return dependencies.list(demandId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable UUID demandId, @Valid @RequestBody CreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return dependencies.create(demandId, request, user);
    }

    @DeleteMapping("/{dependencyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID demandId, @PathVariable UUID dependencyId,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        dependencies.delete(demandId, dependencyId, user);
    }
}
