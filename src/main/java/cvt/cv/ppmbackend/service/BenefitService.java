package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectManagementDtos.BenefitRequest;
import cvt.cv.ppmbackend.dto.ProjectManagementDtos.BenefitResponse;
import cvt.cv.ppmbackend.entity.Benefit;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.ProjectEventType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.BenefitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Benefícios do projeto. Respondem com um DTO (sem o projeto inteiro) e, ao contrário das outras escritas
 * da gestão de projetos, são aceites com o projeto encerrado: os benefícios medem-se depois do fecho.
 */
@Service
@Transactional
public class BenefitService {
    private final BenefitRepository benefits;
    private final ProjectGuard guard;
    private final ProjectEventService events;

    public BenefitService(BenefitRepository benefits, ProjectGuard guard, ProjectEventService events) {
        this.benefits = benefits;
        this.guard = guard;
        this.events = events;
    }

    /** Página {@code /benefits}: todos os benefícios, com o projeto. */
    @Transactional(readOnly = true)
    public List<BenefitResponse> findAll() {
        return benefits.findAll().stream()
                .filter(b -> guard.canView(b.getProject()))
                .sorted(Comparator.comparing(Benefit::getTrackingDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(BenefitService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BenefitResponse get(UUID id) {
        Benefit benefit = require(id);
        if (!guard.canView(benefit.getProject())) {
            throw new ResourceNotFoundException("Benefício não encontrado: " + id);
        }
        return toResponse(benefit);
    }

    @Transactional(readOnly = true)
    public List<BenefitResponse> findByProject(UUID projectId) {
        guard.require(projectId);
        return benefits.findByProjectId(projectId).stream().map(BenefitService::toResponse).toList();
    }

    public BenefitResponse create(BenefitRequest request, String actor) {
        validate(request);
        Project project = guard.requireEditable(request.projectId());
        Benefit benefit = new Benefit();
        benefit.setProject(project);
        benefit.setCreatedBy(actor(actor));
        apply(benefit, request, actor);
        benefits.save(benefit);
        events.log(project, ProjectEventType.BENEFIT_CHANGED, "Benefício " + label(benefit) + " registado", actor);
        return toResponse(benefit);
    }

    public BenefitResponse update(UUID id, BenefitRequest request, String actor) {
        validate(request);
        Benefit benefit = require(id);
        Project project = guard.requireEditable(benefit.getProject().getId());
        if (!project.getId().equals(request.projectId())) {
            throw new BadRequestException("Um benefício não pode mudar de projeto.");
        }
        apply(benefit, request, actor);
        benefits.save(benefit);
        events.log(project, ProjectEventType.BENEFIT_CHANGED, "Benefício " + label(benefit) + " alterado", actor);
        return toResponse(benefit);
    }

    public void delete(UUID id, String actor) {
        Benefit benefit = require(id);
        Project project = guard.requireEditable(benefit.getProject().getId());
        benefits.delete(benefit);
        events.log(project, ProjectEventType.BENEFIT_CHANGED, "Benefício " + label(benefit) + " removido", actor);
    }

    private static void validate(BenefitRequest r) {
        if (r == null || r.projectId() == null) {
            throw new BadRequestException("projectId é obrigatório.");
        }
        if (r.benefitType() == null) {
            throw new BadRequestException("benefitType é obrigatório.");
        }
        if (r.trackingDate() == null) {
            throw new BadRequestException("trackingDate é obrigatório.");
        }
        if (r.expectedValue() == null && r.realizedValue() == null) {
            throw new BadRequestException("Indique o valor esperado ou o valor realizado.");
        }
        if (negative(r.expectedValue()) || negative(r.realizedValue())) {
            throw new BadRequestException("Os valores do benefício não podem ser negativos.");
        }
        if (r.notes() != null && r.notes().length() > 10000) {
            throw new BadRequestException("As notas não podem exceder 10000 caracteres.");
        }
    }

    private static void apply(Benefit benefit, BenefitRequest r, String actor) {
        benefit.setBenefitType(r.benefitType());
        benefit.setExpectedValue(r.expectedValue());
        benefit.setRealizedValue(r.realizedValue());
        benefit.setTrackingDate(r.trackingDate());
        benefit.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        benefit.setUpdatedBy(actor(actor));
    }

    private Benefit require(UUID id) {
        return benefits.findById(id).orElseThrow(() -> new ResourceNotFoundException("Benefício não encontrado: " + id));
    }

    static BenefitResponse toResponse(Benefit b) {
        Project p = b.getProject();
        return new BenefitResponse(b.getId(), p.getId(), p.getCode(), p.getName(), b.getBenefitType(),
                b.getExpectedValue(), b.getRealizedValue(), b.getTrackingDate(), b.getNotes(), b.getCreatedBy(),
                b.getCreatedAt());
    }

    private static boolean negative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private static String label(Benefit benefit) {
        return switch (benefit.getBenefitType()) {
            case REVENUE -> "de receita";
            case EFFICIENCY -> "de eficiência";
            case COMPLIANCE -> "de conformidade";
            case RISK_REDUCTION -> "de redução de risco";
        };
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
