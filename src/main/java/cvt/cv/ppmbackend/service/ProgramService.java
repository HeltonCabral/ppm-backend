package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProgramCreateRequest;
import cvt.cv.ppmbackend.dto.ProgramResponse;
import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.repository.ProgramRepository;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProgramService extends AbstractCrudService<Program, ProgramCreateRequest> {
    private final StrategicObjectiveService objectives;
    private final ProjectRepository projectRepository;

    public ProgramService(ProgramRepository r, StrategicObjectiveService o, ProjectRepository projectRepository) {
        super(r, "Programa");
        objectives = o;
        this.projectRepository = projectRepository;
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

    @Transactional(readOnly = true)
    public List<ProgramResponse> findAllWithProjects() {
        List<Program> programs = ((ProgramRepository) repository).findAll();
        return programs.stream()
                .map(program -> {
                    List<Project> projects = projectRepository.findByProgram_Id(program.getId());
                    double averageProgress = calculateAverageProgress(projects);
                    Set<ProgramResponse.StrategicObjectiveRef> aggregatedObjectives = aggregateStrategicObjectives(projects);
                    return ProgramResponse.from(program, projects, averageProgress, aggregatedObjectives);
                })
                .collect(Collectors.toList());
    }

    private Set<ProgramResponse.StrategicObjectiveRef> aggregateStrategicObjectives(List<Project> projects) {
        return projects.stream()
                .filter(project -> project.getStrategicObjective() != null)
                .map(project -> {
                    StrategicObjective obj = project.getStrategicObjective();
                    return new ProgramResponse.StrategicObjectiveRef(obj.getId(), obj.getName());
                })
                .collect(Collectors.toSet());
    }

    private double calculateAverageProgress(List<Project> projects) {
        if (projects.isEmpty()) {
            return 0.0;
        }

        int totalStatuses = 0;
        int positiveStatuses = 0;

        for (Project project : projects) {
            // Count schedule status
            if (project.getScheduleStatus() != null) {
                totalStatuses++;
                if (isPositiveStatus(project.getScheduleStatus())) {
                    positiveStatuses++;
                }
            }
            // Count cost status
            if (project.getCostStatus() != null) {
                totalStatuses++;
                if (isPositiveStatus(project.getCostStatus())) {
                    positiveStatuses++;
                }
            }
            // Count risk status
            if (project.getRiskStatus() != null) {
                totalStatuses++;
                if (isPositiveStatus(project.getRiskStatus())) {
                    positiveStatuses++;
                }
            }
            // Count value status
            if (project.getValueStatus() != null) {
                totalStatuses++;
                if (isPositiveStatus(project.getValueStatus())) {
                    positiveStatuses++;
                }
            }
        }

        if (totalStatuses == 0) {
            return 0.0;
        }

        return (double) positiveStatuses / totalStatuses * 100;
    }

    private boolean isPositiveStatus(ExecutiveStatus status) {
        return status == ExecutiveStatus.GREEN;
    }
}
