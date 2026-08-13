package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CommitteeDecisionDtos.*;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class CommitteeDecisionService {

    private static final Set<String> VALID_DECISIONS = Set.of(
            "APPROVE", "CONDITIONALLY_APPROVE", "REVISION_REQUESTED", "BACKLOG");

    private static final Set<String> ELIGIBLE_STATUSES = Set.of(
            "IN_STRATEGIC_COMMITTEE", "READY_FOR_COMMITTEE", "PRIORITIZED");

    private final DemandRepository demands;
    private final DemandHistoryService historyService;
    private final DemandScoreLifecycleService scoreLifecycle;

    public CommitteeDecisionService(
            DemandRepository demands,
            DemandHistoryService historyService,
            DemandScoreLifecycleService scoreLifecycle) {
        this.demands = demands;
        this.historyService = historyService;
        this.scoreLifecycle = scoreLifecycle;
    }

    @Transactional
    public BulkDeliberationResponse deliberate(BulkDeliberationRequest req, String actorId) {
        validateDecision(req);

        List<Demand> eligibleDemands = findEligibleDemands(req.filters());

        List<DemandDecisionInfo> processed = new ArrayList<>();
        List<SkippedDemandInfo> skipped = new ArrayList<>();

        for (Demand demand : eligibleDemands) {
            try {
                if (isEligibleForDeliberation(demand)) {
                    applyDecision(demand, req, actorId);
                    processed.add(mapDecisionInfo(demand, req, actorId));
                } else {
                    skipped.add(new SkippedDemandInfo(
                            demand.getId(),
                            demand.getCode(),
                            demand.getTitle(),
                            "Status não elegível para deliberação: " + demand.getStatus()));
                }
            } catch (Exception e) {
                skipped.add(new SkippedDemandInfo(
                        demand.getId(),
                        demand.getCode(),
                        demand.getTitle(),
                        "Erro ao processar: " + e.getMessage()));
            }
        }

        demands.saveAll(eligibleDemands.stream()
                .filter(this::isEligibleForDeliberation)
                .toList());

        return new BulkDeliberationResponse(
                eligibleDemands.size(),
                processed.size(),
                skipped.size(),
                processed,
                skipped);
    }

    private void validateDecision(BulkDeliberationRequest req) {
        String decision = norm(req.decision());
        if (!VALID_DECISIONS.contains(decision)) {
            throw new BadRequestException("Decisão inválida: " + req.decision() +
                    ". Valores permitidos: " + String.join(", ", VALID_DECISIONS));
        }

        if ("REVISION_REQUESTED".equals(decision)) {
            if (req.justification() == null || req.justification().isBlank()) {
                throw new BadRequestException("justification é obrigatória quando decision = REVISION_REQUESTED");
            }
        }
    }

    private List<Demand> findEligibleDemands(BulkDeliberationFilters filters) {
        Specification<Demand> spec = (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                root.get("status").in(ELIGIBLE_STATUSES)
        );

        if (filters.scoreMin() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.greaterThanOrEqualTo(r.get("scoreTotal"), filters.scoreMin()));
        }

        if (filters.scoreMax() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.lessThanOrEqualTo(r.get("scoreTotal"), filters.scoreMax()));
        }

        if (filters.committeeId() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(r.get("responsibleCommittee").get("id"), filters.committeeId()));
        }

        if (filters.directionCode() != null && !filters.directionCode().isBlank()) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(cb.upper(r.get("directionCode")),
                            filters.directionCode().trim().toUpperCase(Locale.ROOT)));
        }

        if (filters.priority() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(cb.upper(r.get("initialPriority")), norm(filters.priority())));
        }

        if (filters.urgency() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(cb.upper(r.get("urgency")), norm(filters.urgency())));
        }

        if (filters.riskLevel() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(cb.upper(r.get("riskStatus")), norm(filters.riskLevel())));
        }

        return demands.findAll(spec);
    }

    private boolean isEligibleForDeliberation(Demand demand) {
        String status = norm(demand.getStatus());
        return ELIGIBLE_STATUSES.contains(status);
    }

    private void applyDecision(Demand demand, BulkDeliberationRequest req, String actorId) {
        String decision = norm(req.decision());
        String previousStatus = demand.getStatus();
        String newStatus;
        String eventType;

        switch (decision) {
            case "APPROVE" -> {
                newStatus = "APPROVED";
                eventType = "APPROVED";
                demand.setCommitteeDecision("APPROVED");
                demand.setApprovalType("NORMAL");
            }
            case "CONDITIONALLY_APPROVE" -> {
                newStatus = "APPROVED";
                eventType = "APPROVED";
                demand.setCommitteeDecision("CONDITIONALLY_APPROVED");
                demand.setApprovalType("CONDITIONAL");
            }
            case "REVISION_REQUESTED" -> {
                newStatus = "IN_ANALYSIS";
                eventType = "REVISION_REQUESTED";
                demand.setCommitteeDecision("REVISION_REQUESTED");
                demand.setRejectionReason(req.justification());
            }
            case "BACKLOG" -> {
                newStatus = "BACKLOG";
                eventType = "MOVED_TO_BACKLOG";
                demand.setCommitteeDecision("BACKLOG");
            }
            default -> throw new BadRequestException("Decisão não reconhecida: " + decision);
        }

        scoreLifecycle.applyStatusTransition(demand, previousStatus, newStatus,
                "Deliberação em mesa: " + decision);

        demand.setStatus(newStatus);
        demand.setInStrategicCommittee(false);
        demand.setUpdatedBy(actor(actorId));

        String description = buildDescription(decision, req);

        historyService.log(demand, eventType, previousStatus, newStatus,
                description, actor(actorId), actor(actorId),
                Map.of("decision", decision,
                       "bulkDeliberation", "true"));
    }

    private String buildDescription(String decision, BulkDeliberationRequest req) {
        String base = switch (decision) {
            case "APPROVE" -> "Demanda aprovada em deliberação de mesa";
            case "CONDITIONALLY_APPROVE" -> "Demanda aprovada condicionalmente em deliberação de mesa";
            case "REVISION_REQUESTED" -> "Solicitada revisão em deliberação de mesa";
            case "BACKLOG" -> "Demanda movida para backlog em deliberação de mesa";
            default -> "Deliberação de mesa: " + decision;
        };

        if (req.condition() != null && !req.condition().isBlank()) {
            base += " - Condição: " + req.condition();
        }

        if (req.justification() != null && !req.justification().isBlank()) {
            base += " - Justificação: " + req.justification();
        }

        return base;
    }

    private DemandDecisionInfo mapDecisionInfo(Demand demand, BulkDeliberationRequest req, String actorId) {
        return new DemandDecisionInfo(
                demand.getId(),
                demand.getCode(),
                demand.getTitle(),
                demand.getStatus(),
                determineNewStatus(req.decision()),
                req.decision(),
                req.condition(),
                req.justification(),
                Instant.now(),
                actor(actorId));
    }

    private String determineNewStatus(String decision) {
        return switch (norm(decision)) {
            case "APPROVE", "CONDITIONALLY_APPROVE" -> "APPROVED";
            case "REVISION_REQUESTED" -> "IN_ANALYSIS";
            case "BACKLOG" -> "BACKLOG";
            default -> null;
        };
    }

    private String norm(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String actor(String value) {
        return value == null || value.isBlank() ? "system" : value;
    }
}
