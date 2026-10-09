package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandRequirementsResolverTest {

    @Mock private DemandResourceDerivationService resourceDerivation;
    @Mock private DemandProfileRequirementRepository profileRequirements;

    private final Profile developer = profile("Programador");

    @Test
    void derivedResourcesWinOverPersistedRequirements() {
        DemandRequirementsResolver resolver = new DemandRequirementsResolver(resourceDerivation, profileRequirements);
        Demand demand = demand();
        DemandProfileRequirement derived = requirement(demand, "1", phase());
        when(resourceDerivation.hasDerivedResources(demand)).thenReturn(true);
        when(resourceDerivation.deriveRequirements(demand)).thenReturn(List.of(derived));

        assertThat(resolver.forDemand(demand)).containsExactly(derived);
        verify(profileRequirements, never()).findByDemand_IdOrderByProfile_NameAsc(demand.getId());
    }

    @Test
    void persistedRequirementsWhenNothingIsDerivedAndEachDemandCountedOnce() {
        DemandRequirementsResolver resolver = new DemandRequirementsResolver(resourceDerivation, profileRequirements);
        Demand demand = demand();
        DemandProfileRequirement persisted = new DemandProfileRequirement();
        when(profileRequirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId())).thenReturn(List.of(persisted));

        assertThat(resolver.forDemands(List.of(demand, demand))).containsExactly(persisted);
    }

    private Demand demand() {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        return demand;
    }

    private DemandProfileRequirement requirement(Demand demand, String fte, PhaseDefinition... phases) {
        DemandProfileRequirement requirement = new DemandProfileRequirement();
        requirement.setDemand(demand);
        requirement.setProfile(developer);
        requirement.setEquivalentLoad(new BigDecimal(fte));
        requirement.setRequiredQuantity(1);
        requirement.setPhases(new LinkedHashSet<>(List.of(phases)));
        return requirement;
    }

    private static PhaseDefinition phase() {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setId(UUID.randomUUID());
        return phase;
    }

    private static Profile profile(String name) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);
        return profile;
    }
}
