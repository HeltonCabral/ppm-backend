package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.entity.BaseEntity;
import cvt.cv.ppmbackend.service.CrudService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public abstract class AbstractCrudController<E extends BaseEntity, R> {
    protected final CrudService<E, R> service;

    protected AbstractCrudController(CrudService<E, R> service) {
        this.service = service;
    }

    @GetMapping
    public Object findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Object findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<E> create(@Valid @RequestBody R request) {
        E saved = service.create(request);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(uri).body(saved);
    }

    @PutMapping("/{id}")
    public E update(@PathVariable UUID id, @Valid @RequestBody R request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
