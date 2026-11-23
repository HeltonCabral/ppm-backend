package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.model.TypeField;
import cvt.cv.ppmbackend.repository.PhaseDefinitionRepository;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import cvt.cv.ppmbackend.repository.PhaseProfileRepository;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Gravação real das colunas JSON (phase_models.fields e demands.type_fields). */
@DataJpaTest
class TypeFieldsJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private PhaseModelRepository models;
    @Autowired private PhaseDefinitionRepository phases;
    @Autowired private PhaseProfileRepository phaseProfiles;
    @Autowired private ProfileRepository profiles;

    private PhaseModelService service;

    @BeforeEach
    void setUp() {
        service = new PhaseModelService(models, phases, phaseProfiles, profiles);
    }

    // 1. Modelo sem campos devolve "fields": [].
    @Test
    void modelWithoutFieldsReturnsEmptyList() {
        ModelResponse created = service.create(request(null));
        em.flush();
        em.clear();

        assertThat(service.get(created.id()).fields()).isEmpty();
    }

    // 2. PUT com fields substitui a lista; PUT sem fields mantém-na.
    @Test
    void putWithFieldsReplacesAndWithoutFieldsKeeps() {
        ModelResponse created = service.create(request(TypeFieldRulesTest.formacaoFields()));
        reload();

        ModelResponse kept = service.update(created.id(), request(null));
        reload();
        assertThat(service.get(kept.id()).fields()).extracting(TypeField::key)
                .containsExactly("modalidade", "local", "participantes");
        TypeField participantes = service.get(kept.id()).fields().get(2);
        assertThat(participantes.min()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(service.get(kept.id()).fields().get(1).showWhen().fieldKey()).isEqualTo("modalidade");

        service.update(created.id(), request(List.of(TypeFieldRulesTest.field("tema", "Tema", "TEXT",
                null, null, null, null, 1))));
        reload();
        assertThat(service.get(created.id()).fields()).extracting(TypeField::key).containsExactly("tema");
    }

    @Test
    void demandTypeFieldsRoundTrip() {
        Demand demand = new Demand();
        demand.setCode("DEM-JSON");
        demand.setTitle("Formação");
        demand.setProblemStatement("Problema");
        demand.setDesiredOutcome("Resultado");
        demand.setTypeFields(new LinkedHashMap<>(Map.of(
                "modalidade", "HIBRIDA", "local", List.of("SANTIAGO", "SAL"), "participantes", 25,
                "certificacao", true)));
        Demand saved = em.persistFlushFind(demand);
        em.clear();

        Demand reloaded = em.find(Demand.class, saved.getId());
        assertThat(reloaded.getTypeFields()).isEqualTo(Map.of(
                "modalidade", "HIBRIDA", "local", List.of("SANTIAGO", "SAL"), "participantes", 25,
                "certificacao", true));
    }

    private void reload() {
        em.flush();
        em.clear();
    }

    private static ModelRequest request(List<TypeField> fields) {
        return new ModelRequest("FORMACAO", "Formação", null, null, false, fields);
    }
}
