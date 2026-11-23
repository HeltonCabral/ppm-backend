package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.ClosureRequest;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.ClosureResponse;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.LessonLearnedResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectClosure;
import cvt.cv.ppmbackend.entity.ProjectLessonLearned;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProjectClosureRepository;
import cvt.cv.ppmbackend.repository.ProjectLessonLearnedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Relatório final do projeto e lições aprendidas. */
@Service
@Transactional
public class ProjectClosureService {
    private final ProjectClosureRepository closures;
    private final ProjectLessonLearnedRepository lessons;
    private final ProjectGuard guard;

    public ProjectClosureService(ProjectClosureRepository closures, ProjectLessonLearnedRepository lessons,
            ProjectGuard guard) {
        this.closures = closures;
        this.lessons = lessons;
        this.guard = guard;
    }

    /** Valida o relatório antes de concluir o projeto: resumo e pelo menos uma lição. */
    public List<String> validate(ClosureRequest closure) {
        if (closure == null || trim(closure.summary()) == null) {
            throw new BadRequestException("O resumo do encerramento é obrigatório.");
        }
        List<String> lessonTexts = new ArrayList<>();
        if (closure.lessonsLearned() != null) {
            closure.lessonsLearned().stream().map(ProjectClosureService::trim)
                    .filter(text -> text != null).forEach(lessonTexts::add);
        }
        if (lessonTexts.isEmpty()) {
            throw new BadRequestException("Indique pelo menos uma lição aprendida.");
        }
        return lessonTexts;
    }

    public void record(Project project, ClosureRequest closure, String actor) {
        List<String> lessonTexts = validate(closure);
        ProjectClosure entity = closures.findByProjectId(project.getId()).orElseGet(ProjectClosure::new);
        entity.setProject(project);
        entity.setSummary(trim(closure.summary()));
        entity.setObjectivesMet(closure.objectivesMet());
        entity.setHandover(trim(closure.handover()));
        entity.setCreatedBy(actor(actor));
        closures.save(entity);
        int sequence = lessons.findByProjectIdOrderBySequenceAsc(project.getId()).size();
        for (String text : lessonTexts) {
            ProjectLessonLearned lesson = new ProjectLessonLearned();
            lesson.setProject(project);
            lesson.setDescription(text);
            lesson.setSequence(++sequence);
            lesson.setCreatedBy(actor(actor));
            lessons.save(lesson);
        }
    }

    @Transactional(readOnly = true)
    public ClosureResponse get(UUID projectId) {
        guard.require(projectId);
        ProjectClosure closure = closures.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Este projeto não tem relatório de encerramento."));
        List<String> lessonTexts = lessons.findByProjectIdOrderBySequenceAsc(projectId).stream()
                .map(ProjectLessonLearned::getDescription).toList();
        return new ClosureResponse(projectId, closure.getSummary(), closure.getObjectivesMet(), closure.getHandover(),
                lessonTexts, closure.getCreatedBy(), closure.getCreatedAt());
    }

    /** Página {@code /lessons}: todas as lições, mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<LessonLearnedResponse> allLessons() {
        return lessons.findAllByOrderByCreatedAtDescSequenceAsc().stream()
                .filter(l -> guard.canView(l.getProject()))
                .map(l -> new LessonLearnedResponse(l.getId(), l.getProject().getId(), l.getProject().getCode(),
                        l.getProject().getName(),
                        l.getCreatedAt() == null ? null : LocalDate.ofInstant(l.getCreatedAt(), ZoneOffset.UTC),
                        l.getDescription(), l.getSequence(), l.getCreatedBy()))
                .toList();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
