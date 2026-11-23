package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectDependencyDtos.Request;
import cvt.cv.ppmbackend.dto.ProjectDependencyDtos.Response;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectDependency;
import cvt.cv.ppmbackend.entity.ProjectMilestone;
import cvt.cv.ppmbackend.enums.ProjectDependencyType;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectDependencyRepository;
import cvt.cv.ppmbackend.repository.ProjectMilestoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class ProjectDependencyService {
    private final ProjectDependencyRepository dependencies;
    private final ProjectMilestoneRepository milestones;
    private final ProjectGuard guard;
    private final ProjectEventService events;

    public ProjectDependencyService(ProjectDependencyRepository dependencies, ProjectMilestoneRepository milestones,
            ProjectGuard guard, ProjectEventService events) {
        this.dependencies = dependencies;
        this.milestones = milestones;
        this.guard = guard;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID projectId) {
        List<ProjectDependency> rows = projectId == null ? dependencies.findAll()
                : dependencies.findByEitherProject(projectId);
        if (projectId != null) {
            Project project = guard.require(projectId);
            if (!guard.canView(project)) throw new ResourceNotFoundException("Projeto não encontrado: " + projectId);
        }
        return rows.stream().filter(d -> guard.canView(d.getPredecessor()) || guard.canView(d.getSuccessor()))
                .map(ProjectDependencyService::toResponse).toList();
    }

    public Response create(Request request, String actor) {
        if (request == null || request.predecessorId() == null || request.successorId() == null) {
            throw new BadRequestException("predecessorId e successorId são obrigatórios.");
        }
        if (request.type() == null) throw new BadRequestException("type é obrigatório.");
        if (request.predecessorId().equals(request.successorId())) {
            throw new BadRequestException("Um projeto não pode depender de si próprio.");
        }
        Project successor = guard.requireEditable(request.successorId());
        Project predecessor = guard.require(request.predecessorId());
        if (createsCycle(predecessor.getId(), successor.getId())) {
            throw new DomainException(HttpStatus.CONFLICT, "PROJECT_DEPENDENCY_CYCLE",
                    "A dependência criaria um ciclo entre projetos.", Map.of());
        }
        ProjectMilestone milestone = null;
        if (request.milestoneId() != null) {
            milestone = milestones.findByIdAndProjectId(request.milestoneId(), predecessor.getId())
                    .orElseThrow(() -> new BadRequestException("O marco tem de pertencer ao projeto predecessor."));
        }
        ProjectDependency dependency = new ProjectDependency();
        dependency.setPredecessor(predecessor);
        dependency.setSuccessor(successor);
        dependency.setType(request.type());
        dependency.setMilestone(milestone);
        dependency.setNotes(trim(request.notes()));
        dependency.setCreatedBy(actor(actor));
        dependency.setUpdatedBy(actor(actor));
        ProjectDependency saved = dependencies.save(dependency);
        String description = "Dependência " + predecessor.getCode() + " → " + successor.getCode() + " criada";
        events.log(successor, ProjectEventType.DEPENDENCY_CHANGED, description, actor);
        events.log(predecessor, ProjectEventType.DEPENDENCY_CHANGED, description, actor);
        return toResponse(saved);
    }

    public void delete(UUID id, String actor) {
        ProjectDependency dependency = dependencies.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dependência não encontrada: " + id));
        guard.requireEditable(dependency.getSuccessor().getId());
        dependencies.delete(dependency);
        String description = "Dependência " + dependency.getPredecessor().getCode() + " → "
                + dependency.getSuccessor().getCode() + " removida";
        events.log(dependency.getSuccessor(), ProjectEventType.DEPENDENCY_CHANGED, description, actor);
        events.log(dependency.getPredecessor(), ProjectEventType.DEPENDENCY_CHANGED, description, actor);
    }

    private boolean createsCycle(UUID predecessorId, UUID successorId) {
        // A nova aresta predecessor -> successor fecha ciclo se successor já alcança predecessor.
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(successorId);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!visited.add(current)) continue;
            if (current.equals(predecessorId)) return true;
            dependencies.findByPredecessorId(current).forEach(d -> queue.addLast(d.getSuccessor().getId()));
        }
        return false;
    }

    public static long conflictDays(ProjectDependency d) {
        Project predecessor = d.getPredecessor();
        Project successor = d.getSuccessor();
        LocalDate predecessorDate;
        LocalDate successorDate;
        if (d.getType() == ProjectDependencyType.START_TO_START) {
            predecessorDate = start(predecessor);
            successorDate = start(successor);
        } else if (d.getType() == ProjectDependencyType.FINISH_TO_FINISH) {
            predecessorDate = finish(predecessor);
            successorDate = finish(successor);
        } else {
            predecessorDate = d.getMilestone() != null ? d.getMilestone().getDueDate() : finish(predecessor);
            successorDate = start(successor);
        }
        if (predecessorDate == null || successorDate == null || !successorDate.isBefore(predecessorDate)) return 0;
        return ChronoUnit.DAYS.between(successorDate, predecessorDate);
    }

    private static LocalDate start(Project p) {
        return p.getExecution() == null ? null : p.getExecution().getPlannedStartDate();
    }

    private static LocalDate finish(Project p) {
        return p.getExecution() == null ? null : p.getExecution().getPlannedEndDate();
    }

    public static Response toResponse(ProjectDependency d) {
        Project p = d.getPredecessor();
        Project s = d.getSuccessor();
        return new Response(d.getId(), p.getId(), p.getCode(), p.getName(), s.getId(), s.getCode(), s.getName(),
                d.getType(), d.getMilestone() == null ? null : d.getMilestone().getId(),
                d.getMilestone() == null ? null : d.getMilestone().getName(), d.getNotes(), conflictDays(d),
                d.getCreatedAt(), d.getCreatedBy());
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String actor(String value) { return value == null || value.isBlank() ? "system" : value.trim(); }
}
