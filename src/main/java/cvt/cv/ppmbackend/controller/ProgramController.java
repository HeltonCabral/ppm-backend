package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.ProgramCreateRequest;
import cvt.cv.ppmbackend.dto.ProgramResponse;
import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.service.ProgramService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/programs")
public class ProgramController extends AbstractCrudController<Program, ProgramCreateRequest> {
    private final ProgramService programService;

    public ProgramController(ProgramService s) {
        super(s);
        this.programService = s;
    }

    @Override
    public List<ProgramResponse> findAll() {
        return programService.findAllWithProjects();
    }

    @Override
    public ProgramResponse findById(@PathVariable UUID id) {
        return ProgramResponse.from(programService.findById(id));
    }
}
