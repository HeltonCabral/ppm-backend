package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.StatusReportRequest;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.StatusReportResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectStatusReport;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.ProjectStatusReportRepository;
import cvt.cv.ppmbackend.repository.ProjectCostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cvt.cv.ppmbackend.service.ProjectEventService.date;

/**
 * Relatórios de estado periódicos. São imutáveis e alimentam a execução: progresso, orçamento consumido
 * e os estados executivos são atualizados na mesma transação.
 */
@Service
@Transactional
public class ProjectStatusReportService {
    private static final int MAX_TEXT = 4000;

    private final ProjectStatusReportRepository reports;
    private final ProjectRepository projects;
    private final ProjectGuard guard;
    private final ProjectPhaseService phases;
    private final ProjectEventService events;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectCostRepository costs;

    public ProjectStatusReportService(ProjectStatusReportRepository reports, ProjectRepository projects,
            ProjectGuard guard, ProjectPhaseService phases, ProjectEventService events) {
        this.reports = reports;
        this.projects = projects;
        this.guard = guard;
        this.phases = phases;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<StatusReportResponse> list(UUID projectId) {
        guard.require(projectId);
        return reports.findByProjectIdOrderByPeriodEndDescCreatedAtDesc(projectId).stream()
                .map(ProjectStatusReportService::toResponse).toList();
    }

    public StatusReportResponse create(UUID projectId, StatusReportRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new DomainException(HttpStatus.CONFLICT, "PROJECT_NOT_IN_PROGRESS",
                    "Só é possível submeter relatórios de estado com o projeto em curso.",
                    Map.of("status", project.getStatus().name()));
        }
        ProjectStatusReport report = validate(request);
        report.setProject(project);
        report.setCreatedBy(actor(actor));
        reports.save(report);

        // O relatório alimenta a execução.
        ProjectExecution execution = project.getExecution();
        if (execution == null) {
            execution = new ProjectExecution();
            project.attachExecution(execution);
        }
        Integer phaseProgress = phases.phaseProgress(projectId);
        execution.setProgress(report.getProgress());
        if (report.getConsumedBudget() != null && (costs == null || !costs.existsByProjectId(projectId))) {
            execution.setConsumedBudget(report.getConsumedBudget());
        }
        execution.setScheduleStatus(report.getScheduleStatus());
        execution.setCostStatus(report.getCostStatus());
        execution.setRiskStatus(report.getRiskStatus());
        execution.setValueStatus(report.getScopeStatus());
        execution.setLastUpdatedAt(Instant.now());
        execution.setLastUpdatedBy(actor(actor));
        project.setLastReportAt(Instant.now());
        project.setLastReportStatus(report.getOverallStatus());
        project.setLastReportNeedsSupport(report.isNeedsSupport());
        projects.save(project);

        events.log(project, ProjectEventType.REPORT_SUBMITTED, "Relatório de estado até " + date(report.getPeriodEnd())
                + ": " + label(report.getOverallStatus()) + ", " + report.getProgress() + "%", actor);
        if (phaseProgress != null && !phaseProgress.equals(report.getProgress())) {
            events.log(project, ProjectEventType.PROGRESS_OVERRIDDEN, "Progresso indicado no relatório ("
                    + report.getProgress() + "%) difere do calculado pelas fases (" + phaseProgress + "%)", actor);
        }
        if (report.isNeedsSupport()) {
            events.log(project, ProjectEventType.SUPPORT_REQUESTED, "O gestor pediu apoio à PMO no relatório até "
                    + date(report.getPeriodEnd()), actor);
        }
        return toResponse(report);
    }

    private ProjectStatusReport validate(StatusReportRequest r) {
        if (r == null) {
            throw new BadRequestException("Dados do relatório em falta.");
        }
        if (r.periodEnd() == null) {
            throw new BadRequestException("periodEnd é obrigatório.");
        }
        if (r.periodEnd().isAfter(LocalDate.now())) {
            throw new BadRequestException("O fim do período não pode ser uma data futura.");
        }
        if (r.overallStatus() == null || r.scheduleStatus() == null || r.costStatus() == null
                || r.scopeStatus() == null || r.riskStatus() == null) {
            throw new BadRequestException("Indique o estado geral, de prazo, de custo, de âmbito e de risco.");
        }
        if (r.progress() == null || r.progress() < 0 || r.progress() > 100) {
            throw new BadRequestException("progress tem de estar entre 0 e 100.");
        }
        if (r.consumedBudget() != null && r.consumedBudget().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("O orçamento consumido não pode ser negativo.");
        }
        String summary = text(r.summary(), "O resumo");
        if (summary == null) {
            throw new BadRequestException("O resumo é obrigatório.");
        }
        String nextSteps = text(r.nextSteps(), "Os próximos passos");
        String blockers = text(r.blockers(), "Os bloqueios");
        if (r.overallStatus() != ExecutiveStatus.GREEN && nextSteps == null && blockers == null) {
            throw new BadRequestException("Com o estado geral " + label(r.overallStatus())
                    + ", indique os bloqueios ou os próximos passos.");
        }
        ProjectStatusReport report = new ProjectStatusReport();
        report.setPeriodEnd(r.periodEnd());
        report.setOverallStatus(r.overallStatus());
        report.setScheduleStatus(r.scheduleStatus());
        report.setCostStatus(r.costStatus());
        report.setScopeStatus(r.scopeStatus());
        report.setRiskStatus(r.riskStatus());
        report.setProgress(r.progress());
        report.setConsumedBudget(r.consumedBudget());
        report.setSummary(summary);
        report.setAchievements(text(r.achievements(), "As realizações"));
        report.setNextSteps(nextSteps);
        report.setBlockers(blockers);
        report.setNeedsSupport(Boolean.TRUE.equals(r.needsSupport()));
        return report;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > MAX_TEXT) {
            throw new BadRequestException(field + " não pode exceder " + MAX_TEXT + " caracteres.");
        }
        return value.trim();
    }

    static StatusReportResponse toResponse(ProjectStatusReport r) {
        return new StatusReportResponse(r.getId(), r.getProject().getId(), r.getPeriodEnd(), r.getOverallStatus(),
                r.getScheduleStatus(), r.getCostStatus(), r.getScopeStatus(), r.getRiskStatus(), r.getProgress(),
                r.getConsumedBudget(), r.getSummary(), r.getAchievements(), r.getNextSteps(), r.getBlockers(),
                r.isNeedsSupport(), r.getCreatedBy(), r.getCreatedAt());
    }

    private static String label(ExecutiveStatus status) {
        return switch (status) {
            case GREEN -> "verde";
            case YELLOW -> "amarelo";
            case RED -> "vermelho";
        };
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
