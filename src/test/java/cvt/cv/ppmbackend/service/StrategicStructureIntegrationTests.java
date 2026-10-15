package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicElementCreateRequest;
import cvt.cv.ppmbackend.dto.StrategicObjectiveCreateRequest;
import cvt.cv.ppmbackend.dto.StrategicObjectiveCreateRequest.AnnualTargetRequest;
import cvt.cv.ppmbackend.dto.StrategicPillarCreateRequest;
import cvt.cv.ppmbackend.entity.StrategicElement;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import cvt.cv.ppmbackend.entity.StrategicPillar;
import cvt.cv.ppmbackend.exception.DomainException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Erros vistos no servidor ao gerir a estrutura estratégica (pilares, elementos, objetivos). */
@SpringBootTest
@Transactional
class StrategicStructureIntegrationTests {
    @Autowired private StrategicPillarService pillars;
    @Autowired private StrategicElementService elements;
    @Autowired private StrategicObjectiveService objectives;
    @Autowired private EntityManager em;

    private StrategicPillar pillar;
    private StrategicElement element;

    @BeforeEach
    void setUp() {
        pillar = pillars.create(new StrategicPillarCreateRequest("Clientes", null, null, 1, null));
        element = elements.create(new StrategicElementCreateRequest("Experiência", null, null, 1, pillar.getId()));
        em.flush();
        em.clear();
    }

    // uk_annual_target_year: voltar a gravar o objetivo com a meta do mesmo ano.
    @Test
    void savingAnObjectiveAgainKeepsOneTargetPerYear() {
        StrategicObjective created = objectives.create(objective(List.of(
                new AnnualTargetRequest(2027, "50% digital", new BigDecimal("50"), new BigDecimal("0.5")),
                new AnnualTargetRequest(2028, "70% digital", new BigDecimal("70"), new BigDecimal("0.5")))));
        em.flush();
        em.clear();

        objectives.update(created.getId(), objective(List.of(
                new AnnualTargetRequest(2027, "55% digital", new BigDecimal("55"), new BigDecimal("0.5")),
                new AnnualTargetRequest(2029, "80% digital", new BigDecimal("80"), new BigDecimal("0.5")))));
        em.flush();
        em.clear();

        StrategicObjective reloaded = objectives.findById(created.getId());
        assertThat(reloaded.getAnnualTargets())
                .extracting(t -> t.getYear() + ":" + t.getTargetLabel())
                .containsExactlyInAnyOrder("2027:55% digital", "2029:80% digital");
    }

    // TransientObjectException: apagar um elemento com objetivos.
    @Test
    void deletingAnElementInUseIsAConflictNotA500() {
        objectives.create(objective(List.of()));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> {
            elements.delete(element.getId());
            em.flush();
        }).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void deletingAPillarInUseIsAConflictNotA500() {
        assertThatThrownBy(() -> {
            pillars.delete(pillar.getId());
            em.flush();
        }).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void unusedElementsAndPillarsCanStillBeDeleted() {
        elements.delete(element.getId());
        em.flush();
        pillars.delete(pillar.getId());
        em.flush();

        assertThat(em.find(StrategicPillar.class, pillar.getId())).isNull();
    }

    private StrategicObjectiveCreateRequest objective(List<AnnualTargetRequest> targets) {
        return new StrategicObjectiveCreateRequest("Digitalizar", null, 2027, 2027, 2029, null, element.getId(), null,
                targets, List.of());
    }
}
