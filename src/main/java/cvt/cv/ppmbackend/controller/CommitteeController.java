package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.CommitteeDtos;
import cvt.cv.ppmbackend.service.CommitteeService;
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
@RequestMapping("/committees")
public class CommitteeController {
    private final CommitteeService committees;

    public CommitteeController(CommitteeService committees) {
        this.committees = committees;
    }

    @GetMapping
    public List<CommitteeDtos.Response> findAll() {
        return committees.findAll();
    }

    @GetMapping("/{id}")
    public CommitteeDtos.Response findById(@PathVariable UUID id) {
        return committees.findById(id);
    }

    @PostMapping
    public ResponseEntity<CommitteeDtos.Response> create(@Valid @RequestBody CommitteeDtos.Request request) {
        CommitteeDtos.Response saved = committees.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.id())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    public CommitteeDtos.Response update(
            @PathVariable UUID id,
            @Valid @RequestBody CommitteeDtos.Request request) {
        return committees.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        committees.delete(id);
    }
}
