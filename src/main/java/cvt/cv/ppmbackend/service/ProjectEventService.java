package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.EventResponse;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectEvent;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.repository.ProjectEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Histórico de eventos do projeto. Cada escrita da gestão de projetos regista aqui uma frase pronta a mostrar. */
@Service
@Transactional
public class ProjectEventService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT = Locale.forLanguageTag("pt-PT");

    private final ProjectEventRepository events;

    public ProjectEventService(ProjectEventRepository events) {
        this.events = events;
    }

    public void log(Project project, ProjectEventType type, String description, String actor) {
        ProjectEvent event = new ProjectEvent();
        event.setProject(project);
        event.setAt(Instant.now());
        event.setActor(actor == null || actor.isBlank() ? "system" : actor.trim());
        event.setType(type);
        event.setDescription(description.length() > 1000 ? description.substring(0, 1000) : description);
        events.save(event);
    }

    /** Mais recente primeiro; {@code size} nulo devolve tudo. */
    @Transactional(readOnly = true)
    public List<EventResponse> list(UUID projectId, Integer page, Integer size) {
        List<ProjectEvent> rows = size == null
                ? events.findByProjectIdOrderByAtDesc(projectId)
                : events.findByProjectIdOrderByAtDesc(projectId,
                        PageRequest.of(page == null ? 0 : page, Math.max(1, size))).getContent();
        return rows.stream()
                .map(e -> new EventResponse(e.getId(), e.getAt(), e.getActor(), e.getType(), e.getDescription()))
                .toList();
    }

    public static String date(LocalDate value) {
        return value == null ? "sem data" : DATE.format(value);
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "sem valor";
        }
        NumberFormat format = NumberFormat.getNumberInstance(PT);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value.setScale(2, RoundingMode.HALF_UP)) + " CVE";
    }
}
