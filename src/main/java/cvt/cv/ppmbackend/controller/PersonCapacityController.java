package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.PersonCapacityDtos.Request;
import cvt.cv.ppmbackend.dto.PersonCapacityDtos.Response;
import cvt.cv.ppmbackend.service.PersonCapacityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Administração › Capacidade das Pessoas. */
@RestController
@RequestMapping("/person-capacities")
public class PersonCapacityController {
    private final PersonCapacityService capacities;

    public PersonCapacityController(PersonCapacityService capacities) {
        this.capacities = capacities;
    }

    @GetMapping
    public List<Response> list(@RequestParam(required = false) String userName) {
        return capacities.list(userName);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody Request request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return capacities.create(request, user);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable UUID id, @RequestBody Request request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return capacities.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        capacities.delete(id);
    }
}
