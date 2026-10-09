package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.model.TypeField;
import cvt.cv.ppmbackend.model.TypeField.Condition;
import cvt.cv.ppmbackend.model.TypeField.Option;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeFieldRulesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ----- Definições -----

    @Test
    void normalizesDefinitionsAndSortsBySequence() {
        List<TypeField> fields = TypeFieldRules.normalizeDefinitions(formacaoFields());

        assertThat(fields).extracting(TypeField::key).containsExactly("modalidade", "local", "participantes");
        TypeField participantes = fields.get(2);
        assertThat(participantes.kind()).isEqualTo("NUMBER");
        assertThat(participantes.active()).isTrue();
        assertThat(participantes.options()).isNull();
        assertThat(fields.get(1).showWhen().values()).containsExactly("PRESENCIAL", "HIBRIDA");
    }

    @Test
    void rejectsRepeatedKey() {
        List<TypeField> fields = List.of(text("nome", 1), text("nome", 2));

        assertBad(fields, "A chave \"nome\" está repetida.");
    }

    @Test
    void rejectsInvalidKeyFormatAndMissingLabelAndKind() {
        assertBad(List.of(text("Nome-Curso", 1)), "letras minúsculas");
        assertBad(List.of(field("nome", null, "TEXT", null, null, null, null, 1)), "o nome é obrigatório");
        assertBad(List.of(field("nome", "Nome", "COLOR", null, null, null, null, 1)), "tipo de campo inválido");
    }

    @Test
    void rejectsSelectWithoutOptionsOrRepeatedOption() {
        assertBad(List.of(field("modalidade", "Modalidade", "SELECT", List.of(), null, null, null, 1)),
                "Modalidade: indique pelo menos uma opção.");
        assertBad(List.of(field("modalidade", "Modalidade", "MULTISELECT",
                List.of(new Option("A", "A"), new Option("A", "Outra")), null, null, null, 1)),
                "a opção \"A\" está repetida");
    }

    @Test
    void rejectsMinGreaterThanMax() {
        assertBad(List.of(field("participantes", "Participantes", "NUMBER", null,
                new BigDecimal("10"), new BigDecimal("1"), null, 1)),
                "Participantes: o mínimo não pode ser maior que o máximo.");
    }

    @Test
    void rejectsConditionOnUnknownOrTextField() {
        assertBad(List.of(text("nome", 1),
                field("local", "Local", "TEXT", null, null, null, new Condition("inexistente", List.of("X")), 2)),
                "Local: a condição tem de apontar para outro campo deste Tipo.");
        assertBad(List.of(text("nome", 1),
                field("local", "Local", "TEXT", null, null, null, new Condition("nome", List.of("X")), 2)),
                "só pode depender de um campo Sim/Não");
    }

    @Test
    void rejectsConditionCycle() {
        List<TypeField> fields = List.of(
                field("a", "Campo A", "BOOLEAN", null, null, null, new Condition("b", List.of("true")), 1),
                field("b", "Campo B", "BOOLEAN", null, null, null, new Condition("a", List.of("true")), 2));

        assertBad(fields, "forma um ciclo");
    }

    @Test
    void booleanConditionOnlyAcceptsTrueOrFalse() {
        assertBad(List.of(
                field("certificacao", "Certificação", "BOOLEAN", null, null, null, null, 1),
                field("entidade", "Entidade", "TEXT", null, null, null,
                        new Condition("certificacao", List.of("sim")), 2)),
                "só aceita os valores \"true\" e \"false\"");
    }

    // ----- Valores -----

    @Test
    void keepsValidValuesAsSent() throws Exception {
        Map<String, Object> values = TypeFieldRules.normalizeValues(formacao(),
                json("{\"modalidade\":\"HIBRIDA\",\"local\":[\"SANTIAGO\",\"SAL\"],\"participantes\":25}"));

        assertThat(values).containsExactly(
                Map.entry("modalidade", "HIBRIDA"),
                Map.entry("local", List.of("SANTIAGO", "SAL")),
                Map.entry("participantes", 25));
    }

    @Test
    void ignoresUnknownKeysAndInactiveFields() throws Exception {
        PhaseModel type = formacao();
        List<TypeField> fields = new ArrayList<>(type.getFields());
        fields.add(field("antigo", "Antigo", "TEXT", null, null, null, null, 4));
        fields.set(3, withActive(fields.get(3), false));
        type.setFields(TypeFieldRules.normalizeDefinitions(fields));

        Map<String, Object> values = TypeFieldRules.normalizeValues(type,
                json("{\"desconhecido\":\"x\",\"antigo\":\"y\",\"participantes\":3}"));

        assertThat(values).containsOnlyKeys("participantes");
    }

    @Test
    void rejectsValueOutsideOptionsOrBelowMinimumWithLabel() {
        assertThatThrownBy(() -> TypeFieldRules.normalizeValues(formacao(), json("{\"modalidade\":\"CORREIO\"}")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Modalidade: opção inválida \"CORREIO\".");
        assertThatThrownBy(() -> TypeFieldRules.normalizeValues(formacao(), json("{\"participantes\":0}")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("N.º de participantes: mínimo 1.");
        assertThatThrownBy(() -> TypeFieldRules.normalizeValues(formacao(), json("{\"participantes\":\"25\"}")))
                .hasMessage("N.º de participantes: tem de ser um número.");
    }

    @Test
    void validatesDatesBooleansAndDirections() {
        PhaseModel type = model(List.of(
                field("inicio", "Data de início", "DATE", null, null, null, null, 1),
                field("certificacao", "Certificação", "BOOLEAN", null, null, null, null, 2),
                field("direcoes", "Direções", "DIRECTIONS", null, null, null, null, 3)));

        assertThat(TypeFieldRules.normalizeValues(type,
                json("{\"inicio\":\"2026-11-02\",\"certificacao\":true,\"direcoes\":[\"DSI\"]}")))
                .containsEntry("inicio", "2026-11-02")
                .containsEntry("certificacao", true)
                .containsEntry("direcoes", List.of("DSI"));
        assertThatThrownBy(() -> TypeFieldRules.normalizeValues(type, json("{\"inicio\":\"02/11/2026\"}")))
                .hasMessage("Data de início: data inválida, use AAAA-MM-DD.");
        assertThatThrownBy(() -> TypeFieldRules.normalizeValues(type, json("{\"certificacao\":\"true\"}")))
                .hasMessage("Certificação: tem de ser verdadeiro ou falso.");
    }

    // ----- Obrigatórios ao sair de Draft -----

    @Test
    void leavingDraftRequiresVisibleRequiredField() {
        Demand demand = demand(Map.of("modalidade", "PRESENCIAL", "participantes", 10));

        assertThatThrownBy(() -> TypeFieldRules.requireVisibleRequired(demand))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Preencha \"Local\" nos Detalhes de Formação.");
                });
    }

    @Test
    void requiredFieldHiddenByConditionIsNotRequired() {
        Demand demand = demand(Map.of("modalidade", "ONLINE", "participantes", 10));

        assertThatCode(() -> TypeFieldRules.requireVisibleRequired(demand)).doesNotThrowAnyException();
    }

    @Test
    void childOfHiddenFieldIsHiddenToo() {
        PhaseModel type = model(List.of(
                field("presencial", "Presencial", "BOOLEAN", null, null, null, null, 1),
                field("ilha", "Ilha", "SELECT", List.of(new Option("SAL", "Sal"), new Option("STG", "Santiago")),
                        null, null, new Condition("presencial", List.of("true")), 2),
                withRequired(field("sala", "Sala", "TEXT", null, null, null,
                        new Condition("ilha", List.of("SAL")), 3))));
        Demand demand = new Demand();
        demand.setType(type);
        // "ilha" tem SAL guardado, mas está escondida porque "presencial" é false.
        demand.setTypeFields(new LinkedHashMap<>(Map.of("presencial", false, "ilha", "SAL")));

        assertThatCode(() -> TypeFieldRules.requireVisibleRequired(demand)).doesNotThrowAnyException();

        demand.getTypeFields().put("presencial", true);
        assertThatThrownBy(() -> TypeFieldRules.requireVisibleRequired(demand))
                .hasMessage("Preencha \"Sala\" nos Detalhes de Formação.");
    }

    // ----- Apoio -----

    private static void assertBad(List<TypeField> fields, String message) {
        assertThatThrownBy(() -> TypeFieldRules.normalizeDefinitions(fields))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(message);
    }

    private static Demand demand(Map<String, Object> values) {
        Demand demand = new Demand();
        demand.setType(formacao());
        demand.setTypeFields(new LinkedHashMap<>(values));
        return demand;
    }

    static PhaseModel formacao() {
        return model(TypeFieldRules.normalizeDefinitions(formacaoFields()));
    }

    static List<TypeField> formacaoFields() {
        List<Option> ilhas = List.of(new Option("SANTIAGO", "Santiago"), new Option("SAL", "Sal"));
        return List.of(
                withRequired(field("participantes", "N.º de participantes", "NUMBER", null, BigDecimal.ONE, null,
                        null, 3)),
                withRequired(field("modalidade", "Modalidade", "SELECT", List.of(
                        new Option("PRESENCIAL", "Presencial"), new Option("ONLINE", "Online"),
                        new Option("HIBRIDA", "Híbrida")), null, null, null, 1)),
                withRequired(field("local", "Local", "MULTISELECT", ilhas, null, null,
                        new Condition("modalidade", List.of("PRESENCIAL", "HIBRIDA")), 2)));
    }

    private static PhaseModel model(List<TypeField> fields) {
        PhaseModel model = new PhaseModel();
        model.setName("Formação");
        model.setFields(TypeFieldRules.normalizeDefinitions(fields));
        return model;
    }

    private static TypeField text(String key, int sequence) {
        return field(key, "Campo " + key, "TEXT", null, null, null, null, sequence);
    }

    static TypeField field(String key, String label, String kind, List<Option> options, BigDecimal min,
            BigDecimal max, Condition showWhen, int sequence) {
        return new TypeField(key, label, kind, false, null, options, null, min, max, showWhen, sequence, null);
    }

    static TypeField withRequired(TypeField f) {
        return new TypeField(f.key(), f.label(), f.kind(), true, f.helpText(), f.options(), f.unit(), f.min(),
                f.max(), f.showWhen(), f.sequence(), f.active());
    }

    private static TypeField withActive(TypeField f, boolean active) {
        return new TypeField(f.key(), f.label(), f.kind(), f.required(), f.helpText(), f.options(), f.unit(),
                f.min(), f.max(), f.showWhen(), f.sequence(), active);
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
