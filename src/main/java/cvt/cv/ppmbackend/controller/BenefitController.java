package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.BenefitRequest;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.BenefitResponse;
import cvt.cv.ppmbackend.service.BenefitService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/benefits")
public class BenefitController {
    private final BenefitService benefits;

    public BenefitController(BenefitService benefits) {
        this.benefits = benefits;
    }

    /** Todos os benefícios, com {@code projectCode}/{@code projectName} (página /benefits). */
    @GetMapping
    public List<BenefitResponse> findAll() {
        return benefits.findAll();
    }

    @GetMapping("/{id}")
    public BenefitResponse findById(@PathVariable UUID id) {
        return benefits.get(id);
    }

    @GetMapping("/project/{id}")
    public List<BenefitResponse> byProject(@PathVariable UUID id) {
        return benefits.findByProject(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BenefitResponse create(@RequestBody BenefitRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return benefits.create(request, user);
    }

    @PutMapping("/{id}")
    public BenefitResponse update(@PathVariable UUID id, @RequestBody BenefitRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String user) {
        return benefits.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader(value = "X-User-Id", required = false) String user) {
        benefits.delete(id, user);
    }
}
