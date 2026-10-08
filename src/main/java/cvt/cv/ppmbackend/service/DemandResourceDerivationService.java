package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandCustomPhase;
import cvt.cv.ppmbackend.entity.DemandCustomResource;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.PhaseProfile;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deriva os recursos previstos de uma Demanda a partir dos perfis das fases ativas do seu Tipo
 * (PhaseModel), filtrados pelas Direções envolvidas na Demanda (responsável + participantes).
 *
 * <p>A necessidade de cada par (perfil, fase) é {@code allocationPercentage / 100} FTE, colocada na
 * janela da respetiva fase. Quando o Tipo não tem perfis configurados, o chamador deve usar os
 * requisitos legados persistidos ({@link DemandProfileRequirement}).
 */
@Service
public class DemandResourceDerivationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final DemandParticipatingDirectionRepository participatingDirections;

    public DemandResourceDerivationService(DemandParticipatingDirectionRepository participatingDirections) {
        this.participatingDirections = participatingDirections;
    }

    /** {@code true} quando o Tipo da Demanda tem pelo menos um perfil associado a uma fase ativa. */
    @Transactional(readOnly = true)
    public boolean hasTypeResources(Demand demand) {
        PhaseModel type = demand == null ? null : demand.getType();
        if (type == null) {
            return false;
        }
        return type.getPhases().stream()
                .filter(PhaseDefinition::isActive)
                .anyMatch(phase -> !phase.getProfiles().isEmpty());
    }

    /** {@code true} quando a Demanda tem planeamento personalizado (fases próprias). */
    @Transactional(readOnly = true)
    public boolean hasCustomPlanning(Demand demand) {
        return demand != null && demand.getCustomPhases() != null && !demand.getCustomPhases().isEmpty();
    }

    /**
     * {@code true} quando os recursos previstos da Demanda devem ser derivados (do planeamento
     * personalizado, quando existe, ou dos perfis das fases do Tipo).
     */
    @Transactional(readOnly = true)
    public boolean hasDerivedResources(Demand demand) {
        return hasCustomPlanning(demand) || hasTypeResources(demand);
    }

    /**
     * Gera requisitos sintéticos (não persistidos), um por par (perfil, fase ativa), para os perfis
     * ativos cujas Direções estão envolvidas na Demanda. Lista vazia quando o Tipo não tem recursos.
     */
    @Transactional(readOnly = true)
    public List<DemandProfileRequirement> deriveRequirements(Demand demand) {
        if (hasCustomPlanning(demand)) {
            return deriveFromCustomPlanning(demand);
        }
        PhaseModel type = demand == null ? null : demand.getType();
        if (type == null) {
            return List.of();
        }
        Set<String> involved = involvedDirectionCodes(demand);
        List<DemandProfileRequirement> derived = new ArrayList<>();
        for (PhaseDefinition phase : type.getPhases()) {
            if (!phase.isActive()) {
                continue;
            }
            for (PhaseProfile phaseProfile : phase.getProfiles()) {
                Profile profile = phaseProfile.getProfile();
                if (profile == null || !profile.isActive()) {
                    continue;
                }
                if (!directionMatches(profile.getDirectionCode(), involved)) {
                    continue;
                }
                derived.add(synthetic(demand, phase, profile, phaseProfile.getAllocationPercentage()));
            }
        }
        return derived;
    }

    /**
     * Requisitos a partir do planeamento personalizado: um por par (perfil, fase ativa), sem filtrar
     * por Direções envolvidas — os recursos personalizados ficam como estão.
     */
    private List<DemandProfileRequirement> deriveFromCustomPlanning(Demand demand) {
        List<DemandProfileRequirement> derived = new ArrayList<>();
        for (DemandCustomPhase phase : demand.getCustomPhases()) {
            if (!phase.isActive()) {
                continue;
            }
            PhaseDefinition phaseRef = customPhaseRef(phase);
            for (DemandCustomResource resource : phase.getResources()) {
                Profile profile = resource.getProfile();
                if (profile == null) {
                    continue;
                }
                derived.add(synthetic(demand, phaseRef, profile, resource.getAllocationPercentage()));
            }
        }
        return derived;
    }

    /**
     * Representação transitória de uma fase personalizada como {@link PhaseDefinition}, para que os
     * requisitos sintéticos e o cálculo de janelas das fases partilhem o mesmo id.
     */
    private PhaseDefinition customPhaseRef(DemandCustomPhase phase) {
        PhaseDefinition ref = new PhaseDefinition();
        ref.setId(phase.getId());
        ref.setName(phase.getName());
        ref.setSequence(phase.getSequence());
        ref.setDurationWeightPercentage(phase.getDurationWeightPercentage());
        ref.setActive(phase.isActive());
        return ref;
    }

    /** Resumo agregado para a complexidade: perfis distintos e soma de FTE. */
    @Transactional(readOnly = true)
    public ResourceSummary summary(Demand demand) {
        List<DemandProfileRequirement> derived = deriveRequirements(demand);
        Set<java.util.UUID> profileIds = new LinkedHashSet<>();
        BigDecimal totalFte = BigDecimal.ZERO;
        for (DemandProfileRequirement req : derived) {
            profileIds.add(req.getProfile().getId());
            totalFte = totalFte.add(req.getEquivalentLoad());
        }
        return new ResourceSummary(profileIds.size(), totalFte.setScale(2, RoundingMode.HALF_UP));
    }

    private DemandProfileRequirement synthetic(Demand demand, PhaseDefinition phase, Profile profile,
            BigDecimal allocationPercentage) {
        BigDecimal fte = allocationPercentage.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        DemandProfileRequirement req = new DemandProfileRequirement();
        req.setDemand(demand);
        req.setProfile(profile);
        req.setEquivalentLoad(fte);
        req.setRequiredQuantity(Math.max(1, fte.setScale(0, RoundingMode.CEILING).intValue()));
        Set<PhaseDefinition> phases = new LinkedHashSet<>();
        phases.add(phase);
        req.setPhases(phases);
        return req;
    }

    private Set<String> involvedDirectionCodes(Demand demand) {
        Set<String> codes = new LinkedHashSet<>();
        addCode(codes, demand.getDirectionCode());
        participatingDirections.findByDemandIdOrderByCreatedAtAsc(demand.getId())
                .forEach(direction -> addCode(codes, direction.getDirectionCode()));
        return codes;
    }

    private void addCode(Set<String> codes, String code) {
        if (code != null && !code.isBlank()) {
            codes.add(code.trim().toUpperCase(Locale.ROOT));
        }
    }

    private boolean directionMatches(String directionCode, Set<String> involved) {
        if (directionCode == null || directionCode.isBlank()) {
            return false;
        }
        return involved.contains(directionCode.trim().toUpperCase(Locale.ROOT));
    }

    public record ResourceSummary(int profilesCount, BigDecimal totalFte) {
    }
}
