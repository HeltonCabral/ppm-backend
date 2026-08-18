package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDependencyDtos.CreateRequest;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.DemandDependencyType;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DemandDependencyServiceTest {
    @Autowired DemandDependencyService dependencies;
    @Autowired DemandRepository demands;
    @Autowired LookupValueRepository lookups;

    private LookupValue type;

    @BeforeEach
    void setUp() {
        type = new LookupValue();
        type.setCategory("DEMAND_TYPE");
        type.setCode("DEPENDENCY_TEST_" + UUID.randomUUID());
        type.setLabel("Teste de dependências");
        type = lookups.save(type);
    }

    @Test
    void createsListsAndDeletesDependencyWithoutChangingScoreTotal() {
        Demand source = demand("SOURCE", "Demanda origem");
        Demand target = demand("TARGET", "Demanda dependência");

        var created = dependencies.create(source.getId(),
                new CreateRequest(target.getId(), DemandDependencyType.BLOCKING, "Aguarda a API"), "analyst");

        assertThat(created.demandId()).isEqualTo(source.getId());
        assertThat(created.dependsOnDemandId()).isEqualTo(target.getId());
        assertThat(created.dependsOnDemandCode()).isEqualTo(target.getCode());
        assertThat(created.dependsOnDemandTitle()).isEqualTo(target.getTitle());
        assertThat(created.type()).isEqualTo(DemandDependencyType.BLOCKING);
        assertThat(dependencies.list(source.getId())).containsExactly(created);

        Demand afterCreate = demands.findById(source.getId()).orElseThrow();
        assertThat(afterCreate.getDependenciesCount()).isEqualTo(1);
        assertThat(afterCreate.getScoreTotal()).isEqualByComparingTo("9.50");

        dependencies.delete(source.getId(), created.dependencyId(), "analyst");

        Demand afterDelete = demands.findById(source.getId()).orElseThrow();
        assertThat(dependencies.list(source.getId())).isEmpty();
        assertThat(afterDelete.getDependenciesCount()).isZero();
        assertThat(afterDelete.getScoreTotal()).isEqualByComparingTo("9.50");
    }

    @Test
    void rejectsSelfDependencyAndDuplicate() {
        Demand source = demand("SELF", "Demanda");
        Demand target = demand("DUPLICATE", "Outra demanda");

        assertThatThrownBy(() -> dependencies.create(source.getId(),
                new CreateRequest(source.getId(), DemandDependencyType.RELATED, null), "analyst"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("própria");

        dependencies.create(source.getId(),
                new CreateRequest(target.getId(), DemandDependencyType.RELATED, null), "analyst");

        assertThatThrownBy(() -> dependencies.create(source.getId(),
                new CreateRequest(target.getId(), DemandDependencyType.BLOCKING, "duplicada"), "analyst"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("já existe");
    }

    @Test
    void rejectsTransitiveCircularDependency() {
        Demand a = demand("A", "Demanda A");
        Demand b = demand("B", "Demanda B");
        Demand c = demand("C", "Demanda C");
        dependencies.create(a.getId(), new CreateRequest(b.getId(), DemandDependencyType.BLOCKING, null), "user");
        dependencies.create(b.getId(), new CreateRequest(c.getId(), DemandDependencyType.RELATED, null), "user");

        assertThatThrownBy(() -> dependencies.create(c.getId(),
                new CreateRequest(a.getId(), DemandDependencyType.BLOCKING, null), "user"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("circular");
    }

    @Test
    void rejectsMissingDemandsAndDependencyOutsideRequestedDemand() {
        Demand source = demand("EXISTS", "Demanda existente");
        Demand other = demand("OTHER", "Outra demanda");
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> dependencies.create(source.getId(),
                new CreateRequest(missing, DemandDependencyType.RELATED, null), "user"))
                .isInstanceOf(ResourceNotFoundException.class);

        var dependency = dependencies.create(source.getId(),
                new CreateRequest(other.getId(), DemandDependencyType.RELATED, null), "user");
        assertThatThrownBy(() -> dependencies.delete(other.getId(), dependency.dependencyId(), "user"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Demand demand(String suffix, String title) {
        Demand demand = new Demand();
        demand.setCode("DDEP-" + suffix);
        demand.setTitle(title);
        demand.setType(type);
        demand.setScoreTotal(new BigDecimal("9.50"));
        return demands.save(demand);
    }
}
