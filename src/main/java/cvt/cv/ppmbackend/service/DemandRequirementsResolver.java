package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fonte única dos recursos (perfil × fase) de uma Demanda. Todos os cálculos de capacidade e a
 * herança de perfis no projeto devem obter os requisitos daqui, para não divergirem; os agregados de
 * capacidade saem todos de {@link CapacityCalculator#summarize}.
 *
 * <p>Regra: derivados do planeamento personalizado ou dos perfis das fases do Tipo quando existem
 * ({@link DemandResourceDerivationService#hasDerivedResources}); caso contrário, os requisitos legados
 * persistidos em {@code demand_profile_requirements}.
 */
@Component
public class DemandRequirementsResolver {
    private final DemandResourceDerivationService resourceDerivation;
    private final DemandProfileRequirementRepository profileRequirements;

    public DemandRequirementsResolver(DemandResourceDerivationService resourceDerivation,
            DemandProfileRequirementRepository profileRequirements) {
        this.resourceDerivation = resourceDerivation;
        this.profileRequirements = profileRequirements;
    }

    public List<DemandProfileRequirement> forDemand(Demand demand) {
        if (resourceDerivation.hasDerivedResources(demand)) {
            return resourceDerivation.deriveRequirements(demand);
        }
        return profileRequirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId());
    }

    /** Requisitos de várias Demandas, cada uma contada uma só vez, pela ordem recebida. */
    public List<DemandProfileRequirement> forDemands(Collection<Demand> demands) {
        List<DemandProfileRequirement> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Demand demand : demands) {
            if (demand != null && seen.add(demand.getId())) {
                out.addAll(forDemand(demand));
            }
        }
        return out;
    }
}
