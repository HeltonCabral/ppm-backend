package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.AccessDtos.DepartmentMappingInput;
import cvt.cv.ppmbackend.dto.AccessDtos.DepartmentMappingResponse;
import cvt.cv.ppmbackend.service.DepartmentMappingService;
import cvt.cv.ppmbackend.service.UserAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Traduções departamento do AD → direção, usadas para sugerir a direção na administração. */
@RestController
@RequestMapping("/department-mappings")
public class DepartmentMappingController {
    private final DepartmentMappingService mappings;
    private final UserAccessService userAccess;

    public DepartmentMappingController(DepartmentMappingService mappings, UserAccessService userAccess) {
        this.mappings = mappings;
        this.userAccess = userAccess;
    }

    @GetMapping
    public List<DepartmentMappingResponse> list() {
        userAccess.requireAdmin();
        return mappings.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentMappingResponse create(@RequestBody DepartmentMappingInput input,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        userAccess.requireAdmin();
        return mappings.create(input, user);
    }

    @PutMapping("/{id}")
    public DepartmentMappingResponse update(@PathVariable UUID id, @RequestBody DepartmentMappingInput input,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        userAccess.requireAdmin();
        return mappings.update(id, input, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userAccess.requireAdmin();
        mappings.delete(id);
    }
}
