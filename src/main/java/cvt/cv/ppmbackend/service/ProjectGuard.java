package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.ProjectStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras comuns às escritas da gestão de projetos: o projeto existe (404), não está encerrado (409) e,
 * com {@code ppm.access.project-filter.enabled}, quem escreve é o gestor do projeto ou tem
 * {@code projeto_editar_todos} (403).
 */
@Component
public class ProjectGuard {
    public static final String CLOSED_MESSAGE = "O projeto está encerrado.";
    private static final Set<ProjectStatus> CLOSED = Set.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED);

    private final ProjectRepository projects;
    private final AccessControlProperties properties;
    private final UserAccessService userAccess;

    public ProjectGuard(ProjectRepository projects, AccessControlProperties properties,
            UserAccessService userAccess) {
        this.projects = projects;
        this.properties = properties;
        this.userAccess = userAccess;
    }

    public Project require(UUID projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado: " + projectId));
    }

    /** Projeto que pode ser escrito: existe, o utilizador pode editá-lo e não está encerrado. */
    public Project requireWritable(UUID projectId) {
        Project project = requireEditable(projectId);
        requireOpen(project);
        return project;
    }

    /** Como {@link #requireWritable}, mas aceita projetos encerrados (benefícios medem-se depois do fecho). */
    public Project requireEditable(UUID projectId) {
        Project project = require(projectId);
        if (properties.projectFilterEnabled() && !canEdit(project)) {
            throw forbidden("Não tem permissão para editar este projeto.");
        }
        return project;
    }

    public void requireOpen(Project project) {
        if (isClosed(project)) {
            throw new DomainException(HttpStatus.CONFLICT, "PROJECT_CLOSED", CLOSED_MESSAGE,
                    Map.of("status", project.getStatus().name()));
        }
    }

    public static boolean isClosed(Project project) {
        return CLOSED.contains(project.getStatus());
    }

    /** Decidir pedidos de alteração: com o filtro ligado, só {@code projeto_editar_todos}. */
    public void requireEditAll(String message) {
        if (properties.projectFilterEnabled() && !userAccess.current().has(UserAccess.PROJECT_EDIT_ALL)) {
            throw forbidden(message);
        }
    }

    public boolean filterEnabled() {
        return properties.projectFilterEnabled();
    }

    public UserAccess currentUser() {
        return userAccess.current();
    }

    /**
     * Regra "Ver" provisória (a definitiva está em ACESSO-PROJETOS-BACKEND.md): sem filtro, tudo; com filtro,
     * {@code projeto_ver_todos}, {@code projeto_editar_todos} ou o gestor do projeto.
     */
    public boolean canView(Project project) {
        if (!properties.projectFilterEnabled()) {
            return true;
        }
        return userAccess.current().has(UserAccess.PROJECT_VIEW_ALL) || canEdit(project);
    }

    private boolean canEdit(Project project) {
        UserAccess access = userAccess.current();
        return access.has(UserAccess.PROJECT_EDIT_ALL)
                || (project.getProjectManagerUsername() != null
                        && project.getProjectManagerUsername().trim().equalsIgnoreCase(access.userName()));
    }

    private static DomainException forbidden(String message) {
        return new DomainException(HttpStatus.FORBIDDEN, "PROJECT_EDIT_FORBIDDEN", message, Map.of());
    }
}
