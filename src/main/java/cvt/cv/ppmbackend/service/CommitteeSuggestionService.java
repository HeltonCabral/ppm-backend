package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CommitteeAlternativeResponse;
import cvt.cv.ppmbackend.dto.CommitteeSuggestionResponse;
import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.repository.CommitteeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CommitteeSuggestionService {
    private static final String DIRECTION_REASON = "Direção solicitante aplicável ao Comité.";
    private static final String DEMAND_TYPE_REASON = "Tipo da demanda aplicável ao Comité.";
    private static final String DOMAIN_REASON = "Domínio da demanda aplicável ao Comité.";
    private static final String RISK_REASON = "Nível de risco da demanda aplicável ao Comité.";
    private static final String BUDGET_REASON = "Orçamento estimado atinge o mínimo definido pelo Comité.";

    private final CommitteeRepository committees;

    public CommitteeSuggestionService(CommitteeRepository committees) {
        this.committees = committees;
    }

    public CommitteeSuggestionResponse suggest(Demand demand) {
        return calculate(demand).response();
    }

    public SuggestionResult calculate(Demand demand) {
        List<ScoredCommittee> ranked = committees.findByStatusOrderByNameAsc(CommitteeStatus.ACTIVE).stream()
                .map(committee -> score(committee, demand))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingInt(ScoredCommittee::score).reversed()
                        .thenComparing(candidate -> normalized(candidate.committee().getName()))
                        .thenComparing(candidate -> candidate.committee().getId().toString()))
                .toList();

        if (ranked.isEmpty()) {
            return new SuggestionResult(null,
                    new CommitteeSuggestionResponse(null, null, null, 0, List.of(), List.of()));
        }

        ScoredCommittee suggested = ranked.get(0);
        List<CommitteeAlternativeResponse> alternatives = ranked.stream()
                .skip(1)
                .map(this::alternative)
                .toList();
        Committee committee = suggested.committee();
        CommitteeSuggestionResponse response = new CommitteeSuggestionResponse(
                committee.getId(),
                committee.getName(),
                committee.getNameKey(),
                suggested.score(),
                suggested.reasons(),
                alternatives);
        return new SuggestionResult(committee, response);
    }

    private ScoredCommittee score(Committee committee, Demand demand) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (matches(committee.getDirections(), demand.getDirection())) {
            score += 20;
            reasons.add(DIRECTION_REASON);
        }

        LookupValue type = demand.getType();
        if (matches(committee.getDemandTypes(),
                type != null ? type.getCode() : null,
                type != null ? type.getLabel() : null)) {
            score += 30;
            reasons.add(DEMAND_TYPE_REASON);
        }

        LookupValue domain = demand.getDomain();
        if (matches(committee.getDomains(),
                domain != null ? domain.getCode() : null,
                domain != null ? domain.getLabel() : null)) {
            score += 40;
            reasons.add(DOMAIN_REASON);
        }

        if (matchesRisk(committee.getRiskLevels(), demand.getRiskStatus())) {
            score += 10;
            reasons.add(RISK_REASON);
        }

        if (committee.getMinimumBudget() != null
                && demand.getEstimatedBudget() != null
                && demand.getEstimatedBudget().compareTo(committee.getMinimumBudget()) >= 0) {
            score += 30;
            reasons.add(BUDGET_REASON);
        }

        return new ScoredCommittee(committee, score, List.copyOf(reasons));
    }

    private boolean matches(List<String> configuredValues, String... demandValues) {
        if (configuredValues == null || configuredValues.isEmpty()) {
            return false;
        }
        for (String configured : configuredValues) {
            String normalizedConfigured = normalized(configured);
            for (String demandValue : demandValues) {
                if (!normalizedConfigured.isEmpty() && normalizedConfigured.equals(normalized(demandValue))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesRisk(List<String> configuredValues, String demandRisk) {
        if (configuredValues == null || configuredValues.isEmpty() || demandRisk == null) {
            return false;
        }
        String canonicalDemandRisk = canonicalRisk(demandRisk);
        return configuredValues.stream()
                .map(this::canonicalRisk)
                .anyMatch(canonicalDemandRisk::equals);
    }

    private String canonicalRisk(String risk) {
        return switch (normalized(risk)) {
            case "baixo", "low" -> "LOW";
            case "medio", "medium" -> "MEDIUM";
            case "alto", "high" -> "HIGH";
            case "critico", "critical" -> "CRITICAL";
            case "nao avaliado", "not evaluated" -> "NOT_EVALUATED";
            default -> normalized(risk).toUpperCase(Locale.ROOT);
        };
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private CommitteeAlternativeResponse alternative(ScoredCommittee candidate) {
        Committee committee = candidate.committee();
        return new CommitteeAlternativeResponse(
                committee.getId(),
                committee.getName(),
                committee.getNameKey(),
                candidate.score(),
                candidate.reasons());
    }

    public record SuggestionResult(Committee suggestedCommittee, CommitteeSuggestionResponse response) {
    }

    private record ScoredCommittee(Committee committee, int score, List<String> reasons) {
    }
}
