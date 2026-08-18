package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementInput;
import cvt.cv.ppmbackend.dto.ProfileDtos.CreateRequest;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.ProfileCategory;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProfileCapacityServiceTest {
    @Autowired ProfileService profiles;
    @Autowired DemandProfileRequirementService requirements;
    @Autowired DemandRepository demands;
    @Autowired LookupValueRepository lookups;

    @Test
    void managesProfileLifecycleAndValidatesUniqueNamesAndCapacity() {
        var created = profiles.create(new CreateRequest("Analista Funcional", ProfileCategory.TECHNOLOGY,
                "Análise funcional", 4));

        assertThat(created.active()).isTrue();
        assertThat(created.availableCapacity()).isEqualTo(4);
        assertThat(profiles.listActive()).extracting("id").contains(created.id());
        assertThat(profiles.deactivate(created.id()).active()).isFalse();
        assertThat(profiles.listActive()).extracting("id").doesNotContain(created.id());
        assertThat(profiles.activate(created.id()).active()).isTrue();

        assertThatThrownBy(() -> profiles.create(new CreateRequest("analista funcional",
                ProfileCategory.OTHER, null, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Já existe");
        assertThatThrownBy(() -> profiles.create(new CreateRequest("Inválido", ProfileCategory.OTHER, null, -1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("availableCapacity");
    }

    @Test
    void rejectsInactiveAndDuplicateNewRequirementsButPreservesExistingOnes() {
        Demand demand = demand();
        var profile = profiles.create(new CreateRequest("Especialista de Risco", ProfileCategory.RISK, null, 2));
        profiles.deactivate(profile.id());

        DemandProfileRequirementInput input = new DemandProfileRequirementInput(profile.id(), 1, 50);
        assertThatThrownBy(() -> requirements.sync(demand, List.of(input)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inativos");

        profiles.activate(profile.id());
        assertThatThrownBy(() -> requirements.sync(demand, List.of(input, input)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> requirements.sync(demand,
                List.of(new DemandProfileRequirementInput(profile.id(), 1, 30))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("25, 50, 75 ou 100");

        requirements.sync(demand, List.of(input));
        profiles.deactivate(profile.id());
        requirements.sync(demand, List.of(new DemandProfileRequirementInput(profile.id(), 2, 75)));

        var saved = requirements.list(demand.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).requiredQuantity()).isEqualTo(2);
        assertThat(saved.get(0).profileActive()).isFalse();
        assertThat(demands.findById(demand.getId()).orElseThrow().getScoreTotal())
                .isEqualByComparingTo("8.75");
    }

    private Demand demand() {
        LookupValue type = new LookupValue();
        type.setCategory("DEMAND_TYPE");
        type.setCode("PROFILE_CAPACITY_TEST");
        type.setLabel("Teste de perfis");
        type = lookups.save(type);

        Demand demand = new Demand();
        demand.setCode("DEM-PROFILE-TEST");
        demand.setTitle("Demanda com perfis");
        demand.setType(type);
        demand.setScoreTotal(new BigDecimal("8.75"));
        return demands.save(demand);
    }
}
