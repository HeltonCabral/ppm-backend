package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectExecutionResponse;
import cvt.cv.ppmbackend.dto.ProjectExecutionUpdateRequest;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.ProjectExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class ProjectExecutionService {
    private final ProjectExecutionRepository executions;
    private final ProjectService projects;

    public ProjectExecutionService(ProjectExecutionRepository executions, ProjectService projects) {
        this.executions = executions;
        this.projects = projects;
    }

    public ProjectExecutionResponse get(UUID projectId) {
        return ProjectExecutionResponse.from(ensureExecution(projectId));
    }

    public ProjectExecutionResponse update(UUID projectId, ProjectExecutionUpdateRequest request, String actor) {
        validateDates(request.plannedStartDate(), request.plannedEndDate(), "planeadas");
        validateDates(request.actualStartDate(), request.actualEndDate(), "reais");
        if (request.progress() < 0 || request.progress() > 100)
            throw new BadRequestException("progress deve estar entre 0 e 100");
        if (request.consumedBudget().compareTo(BigDecimal.ZERO) < 0)
            throw new BadRequestException("consumedBudget não pode ser negativo");

        ProjectExecution e = ensureExecution(projectId);
        e.setProgress(request.progress());
        e.setConsumedBudget(request.consumedBudget());
        e.setPlannedStartDate(request.plannedStartDate());
        e.setActualStartDate(request.actualStartDate());
        e.setPlannedEndDate(request.plannedEndDate());
        e.setActualEndDate(request.actualEndDate());
        e.setScheduleStatus(request.scheduleStatus());
        e.setCostStatus(request.costStatus());
        e.setRiskStatus(request.riskStatus());
        e.setValueStatus(request.valueStatus());
        e.setRisk(request.risk());
        e.setDelayReasons(request.delayReasons());
        e.setExecutionNotes(request.executionNotes());
        e.setLastUpdatedAt(Instant.now());
        e.setLastUpdatedBy(actor == null || actor.isBlank() ? "system" : actor.trim());
        return ProjectExecutionResponse.from(executions.save(e));
    }

    private ProjectExecution ensureExecution(UUID projectId) {
        Project project = projects.findById(projectId);
        return executions.findByProject_Id(projectId).orElseGet(() -> {
            ProjectExecution execution = new ProjectExecution();
            execution.setProject(project);
            execution.setLastUpdatedAt(Instant.now());
            execution.setLastUpdatedBy("system");
            project.attachExecution(execution);
            return executions.save(execution);
        });
    }

    private void validateDates(LocalDate start, LocalDate end, String label) {
        if (start != null && end != null && start.isAfter(end))
            throw new BadRequestException("Datas " + label + ": início não pode ser posterior ao fim");
    }
}
