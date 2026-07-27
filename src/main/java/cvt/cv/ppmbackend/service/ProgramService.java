package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProgramCreateRequest;
import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class ProgramService extends AbstractCrudService<Program, ProgramCreateRequest> {
    private final StrategicObjectiveService objectives;

    public ProgramService(ProgramRepository r, StrategicObjectiveService o) {
        super(r, "Programa");
        objectives = o;
    }

    protected Program newEntity() {
        return new Program();
    }

    protected Program apply(ProgramCreateRequest r, Program e) {
        e.setName(r.name());
        e.setDescription(r.description());
        e.setProgramManager(r.programManager());
        e.setStrategicObjectives(r.strategicObjectiveIds().stream()
                .map(objectives::findById)
                .collect(Collectors.toSet()));
        return e;
    }
}
