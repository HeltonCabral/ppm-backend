package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProfileDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.ProfileDtos.Response;
import cvt.cv.ppmbackend.dto.ProfileDtos.UpdateRequest;
import cvt.cv.ppmbackend.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/profiles")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public List<Response> list() {
        return profiles.list();
    }

    @GetMapping("/active")
    public List<Response> listActive() {
        return profiles.listActive();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return profiles.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest request) {
        return profiles.create(request);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return profiles.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    public Response activate(@PathVariable UUID id) {
        return profiles.activate(id);
    }

    @PatchMapping("/{id}/deactivate")
    public Response deactivate(@PathVariable UUID id) {
        return profiles.deactivate(id);
    }
}
