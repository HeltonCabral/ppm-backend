package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.RiskRequest;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.RiskResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.entity.ProjectRisk;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.enums.ProjectRiskKind;
import cvt.cv.ppmbackend.enums.ProjectRiskStatus;
import cvt.cv.ppmbackend.enums.RiskLevel;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import cvt.cv.ppmbackend.repository.ProjectRiskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Registo de riscos e problemas. Depois de cada escrita, {@code execution.risk} passa a ser o nível mais
 * alto entre os registos abertos (OPEN ou MITIGATING), ou LOW se não houver nenhum.
 */
@Service
@Transactional
public class ProjectRiskService {
    private static final Set<ProjectRiskStatus> OPEN = EnumSet.of(ProjectRiskStatus.OPEN, ProjectRiskStatus.MITIGATING);

    private final ProjectRiskRepository risks;
    private final ProjectRepository projects;
    private final ProjectGuard guard;
    private final ProjectEventService events;

    public ProjectRiskService(ProjectRiskRepository risks, ProjectRepository projects, ProjectGuard guard,
            ProjectEventService events) {
        this.risks = risks;
        this.projects = projects;
        this.guard = guard;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<RiskResponse> list(UUID projectId) {
        guard.require(projectId);
        return risks.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(ProjectRiskService::toResponse).toList();
    }

    /** Página {@code /risks}: riscos de todos os projetos, filtrados por estado e nível, mais graves primeiro. */
    @Transactional(readOnly = true)
    public List<RiskResponse> search(Collection<ProjectRiskStatus> statuses, Collection<RiskLevel> levels) {
        Collection<ProjectRiskStatus> wanted = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(ProjectRiskStatus.class) : statuses;
        return risks.findByStatusIn(wanted).stream()
                .filter(r -> levels == null || levels.isEmpty() || levels.contains(level(score(r))))
                .sorted(Comparator.comparingInt(ProjectRiskService::score).reversed()
                        .thenComparing(ProjectRisk::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ProjectRiskService::toResponse)
                .toList();
    }

    public RiskResponse create(UUID projectId, RiskRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectRisk risk = new ProjectRisk();
        risk.setProject(project);
        risk.setCreatedBy(actor(actor));
        apply(risk, request, actor);
        risks.save(risk);
        refreshProjectRisk(project, actor);
        events.log(project, ProjectEventType.RISK_OPENED, label(risk) + " " + risk.getTitle() + " registado (nível "
                + levelLabel(level(score(risk))) + ")", actor);
        return toResponse(risk);
    }

    public RiskResponse update(UUID projectId, UUID riskId, RiskRequest request, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectRisk risk = require(projectId, riskId);
        boolean wasOpen = OPEN.contains(risk.getStatus());
        apply(risk, request, actor);
        risks.save(risk);
        refreshProjectRisk(project, actor);
        boolean closed = wasOpen && risk.getStatus() == ProjectRiskStatus.CLOSED;
        events.log(project, closed ? ProjectEventType.RISK_CLOSED : ProjectEventType.RISK_UPDATED,
                label(risk) + " " + risk.getTitle() + (closed ? " fechado" : " alterado"), actor);
        return toResponse(risk);
    }

    public void delete(UUID projectId, UUID riskId, String actor) {
        Project project = guard.requireWritable(projectId);
        ProjectRisk risk = require(projectId, riskId);
        risks.delete(risk);
        risks.flush();
        refreshProjectRisk(project, actor);
        events.log(project, ProjectEventType.RISK_REMOVED, label(risk) + " " + risk.getTitle() + " removido", actor);
    }

    private void apply(ProjectRisk risk, RiskRequest r, String actor) {
        if (r == null) {
            throw new BadRequestException("Dados do risco em falta.");
        }
        if (r.kind() == null) {
            throw new BadRequestException("kind é obrigatório (RISK ou ISSUE).");
        }
        String title = trim(r.title());
        if (title == null) {
            throw new BadRequestException("O título é obrigatório.");
        }
        if (title.length() > 180) {
            throw new BadRequestException("O título não pode exceder 180 caracteres.");
        }
        Integer probability = r.kind() == ProjectRiskKind.ISSUE ? Integer.valueOf(5) : r.probability();
        if (probability == null || probability < 1 || probability > 5) {
            throw new BadRequestException("A probabilidade tem de estar entre 1 e 5.");
        }
        if (r.impact() == null || r.impact() < 1 || r.impact() > 5) {
            throw new BadRequestException("O impacto tem de estar entre 1 e 5.");
        }
        ProjectRiskStatus status = r.status() == null ? ProjectRiskStatus.OPEN : r.status();
        String response = trim(r.response());
        RiskLevel level = level(probability * r.impact());
        if (status != ProjectRiskStatus.CLOSED && level != RiskLevel.LOW && response == null) {
            throw new BadRequestException("Com nível " + levelLabel(level)
                    + ", indique o plano de resposta ou de resolução.");
        }
        String owner = trim(r.owner());
        if (owner != null && owner.length() > 150) {
            throw new BadRequestException("O responsável não pode exceder 150 caracteres.");
        }
        risk.setKind(r.kind());
        risk.setTitle(title);
        risk.setDescription(trim(r.description()));
        risk.setProbability(probability);
        risk.setImpact(r.impact());
        risk.setStatus(status);
        risk.setResponse(response);
        risk.setOwner(owner);
        risk.setDueDate(r.dueDate());
        risk.setUpdatedBy(actor(actor));
    }

    /** {@code execution.risk} = nível mais alto entre os registos abertos; LOW sem nenhum. */
    private void refreshProjectRisk(Project project, String actor) {
        RiskLevel highest = risks.findByProjectIdAndStatusIn(project.getId(), OPEN).stream()
                .map(r -> level(score(r)))
                .max(Comparator.naturalOrder())
                .orElse(RiskLevel.LOW);
        ProjectExecution execution = project.getExecution();
        if (execution == null) {
            execution = new ProjectExecution();
            project.attachExecution(execution);
        }
        execution.setRisk(highest);
        execution.setLastUpdatedAt(Instant.now());
        execution.setLastUpdatedBy(actor(actor));
        projects.save(project);
    }

    private ProjectRisk require(UUID projectId, UUID riskId) {
        return risks.findByIdAndProjectId(riskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Risco não encontrado neste projeto: " + riskId));
    }

    static int score(ProjectRisk risk) {
        return risk.getProbability() * risk.getImpact();
    }

    /** 1–4 LOW, 5–9 MEDIUM, 10–15 HIGH, 16–25 CRITICAL. */
    static RiskLevel level(int score) {
        if (score >= 16) return RiskLevel.CRITICAL;
        if (score >= 10) return RiskLevel.HIGH;
        if (score >= 5) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    static RiskResponse toResponse(ProjectRisk r) {
        Project p = r.getProject();
        int score = score(r);
        return new RiskResponse(r.getId(), p.getId(), p.getCode(), p.getName(), r.getKind(), r.getTitle(),
                r.getDescription(), r.getProbability(), r.getImpact(), score, level(score), r.getStatus(),
                r.getResponse(), r.getOwner(), r.getDueDate(), r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedBy(),
                r.getUpdatedAt());
    }

    private static String label(ProjectRisk risk) {
        return risk.getKind() == ProjectRiskKind.ISSUE ? "Problema" : "Risco";
    }

    private static String levelLabel(RiskLevel level) {
        return switch (level) {
            case LOW -> "baixo";
            case MEDIUM -> "médio";
            case HIGH -> "alto";
            case CRITICAL -> "crítico";
        };
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
