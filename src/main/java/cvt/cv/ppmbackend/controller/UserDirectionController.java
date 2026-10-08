package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.AccessDtos.BulkResult;
import cvt.cv.ppmbackend.dto.AccessDtos.UserDirectionInput;
import cvt.cv.ppmbackend.dto.AccessDtos.UserDirectionResponse;
import cvt.cv.ppmbackend.service.UserAccessService;
import cvt.cv.ppmbackend.service.UserDirectionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Administração › Utilizadores por Direção. */
@RestController
@RequestMapping("/user-directions")
public class UserDirectionController {
    private final UserDirectionService userDirections;
    private final UserAccessService userAccess;

    public UserDirectionController(UserDirectionService userDirections, UserAccessService userAccess) {
        this.userDirections = userDirections;
        this.userAccess = userAccess;
    }

    @GetMapping
    public List<UserDirectionResponse> list() {
        userAccess.requireAdmin();
        return userDirections.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDirectionResponse create(@RequestBody UserDirectionInput input,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        userAccess.requireAdmin();
        return userDirections.create(input, user);
    }

    @PostMapping("/bulk")
    public BulkResult bulk(@RequestBody List<UserDirectionInput> inputs,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        userAccess.requireAdmin();
        return userDirections.bulk(inputs, user);
    }

    @PutMapping("/{id}")
    public UserDirectionResponse update(@PathVariable UUID id, @RequestBody UserDirectionInput input,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        userAccess.requireAdmin();
        return userDirections.update(id, input, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userAccess.requireAdmin();
        userDirections.delete(id);
    }
}
