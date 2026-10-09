package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.enums.TypeFieldKind;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.model.TypeField;
import cvt.cv.ppmbackend.model.TypeField.Condition;
import cvt.cv.ppmbackend.model.TypeField.Option;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Campos específicos por Tipo de Demanda: validação das definições no modelo de fases, validação dos
 * valores na demanda ({@code typeFields}) e exigência dos obrigatórios visíveis ao sair de Draft.
 */
public final class TypeFieldRules {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_]{1,40}$");
    private static final int MAX_LABEL = 120;
    private static final Set<TypeFieldKind> OPTION_KINDS = EnumSet.of(TypeFieldKind.SELECT, TypeFieldKind.MULTISELECT);
    private static final Set<TypeFieldKind> NUMERIC_KINDS = EnumSet.of(TypeFieldKind.NUMBER, TypeFieldKind.CURRENCY);
    private static final Set<TypeFieldKind> CONDITION_KINDS =
            EnumSet.of(TypeFieldKind.BOOLEAN, TypeFieldKind.SELECT, TypeFieldKind.MULTISELECT);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TypeFieldRules() {
    }

    // ----- Definições (modelo de fases) -----

    /** Valida a lista inteira e devolve-a normalizada e ordenada por {@code sequence}. */
    public static List<TypeField> normalizeDefinitions(List<TypeField> input) {
        if (input == null) {
            return new ArrayList<>();
        }
        List<TypeField> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            TypeField field = input.get(i);
            if (field == null) {
                throw bad("Campo " + (i + 1) + ": definição em falta.");
            }
            String key = trim(field.key());
            if (key == null) {
                throw bad("Campo " + (i + 1) + ": a chave é obrigatória.");
            }
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw bad("Campo \"" + key + "\": a chave só pode ter letras minúsculas, números e _ "
                        + "(até 40 caracteres).");
            }
            if (!keys.add(key)) {
                throw bad("A chave \"" + key + "\" está repetida.");
            }
            String label = trim(field.label());
            if (label == null) {
                throw bad("Campo \"" + key + "\": o nome é obrigatório.");
            }
            if (label.length() > MAX_LABEL) {
                throw bad(label + ": o nome não pode exceder " + MAX_LABEL + " caracteres.");
            }
            TypeFieldKind kind = parseKind(field.kind(), label);
            boolean numeric = NUMERIC_KINDS.contains(kind);
            BigDecimal min = numeric ? field.min() : null;
            BigDecimal max = numeric ? field.max() : null;
            if (min != null && max != null && min.compareTo(max) > 0) {
                throw bad(label + ": o mínimo não pode ser maior que o máximo.");
            }
            result.add(new TypeField(
                    key,
                    label,
                    kind.name(),
                    Boolean.TRUE.equals(field.required()),
                    trim(field.helpText()),
                    OPTION_KINDS.contains(kind) ? normalizeOptions(field.options(), label) : null,
                    numeric ? trim(field.unit()) : null,
                    min,
                    max,
                    normalizeCondition(field.showWhen(), label),
                    field.sequence() != null ? field.sequence() : i + 1,
                    !Boolean.FALSE.equals(field.active())));
        }
        validateConditions(result);
        result.sort(Comparator.comparing(TypeField::sequence));
        return result;
    }

    private static TypeFieldKind parseKind(String raw, String label) {
        String value = trim(raw);
        if (value != null) {
            try {
                return TypeFieldKind.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // cai na mensagem abaixo
            }
        }
        throw bad(label + ": tipo de campo inválido" + (value == null ? "" : " \"" + value + "\"") + ".");
    }

    private static List<Option> normalizeOptions(List<Option> options, String label) {
        if (options == null || options.isEmpty()) {
            throw bad(label + ": indique pelo menos uma opção.");
        }
        List<Option> result = new ArrayList<>();
        Set<String> values = new HashSet<>();
        for (Option option : options) {
            String value = option == null ? null : trim(option.value());
            if (value == null) {
                throw bad(label + ": as opções não podem ter valor vazio.");
            }
            if (!values.add(value)) {
                throw bad(label + ": a opção \"" + value + "\" está repetida.");
            }
            String optionLabel = trim(option.label());
            result.add(new Option(value, optionLabel != null ? optionLabel : value));
        }
        return result;
    }

    private static Condition normalizeCondition(Condition condition, String label) {
        if (condition == null) {
            return null;
        }
        String fieldKey = trim(condition.fieldKey());
        if (fieldKey == null) {
            throw bad(label + ": a condição tem de indicar o campo de que depende.");
        }
        Set<String> values = new LinkedHashSet<>();
        if (condition.values() != null) {
            condition.values().stream().map(TypeFieldRules::trim).filter(v -> v != null).forEach(values::add);
        }
        if (values.isEmpty()) {
            throw bad(label + ": a condição tem de indicar pelo menos um valor.");
        }
        return new Condition(fieldKey, List.copyOf(values));
    }

    private static void validateConditions(List<TypeField> fields) {
        Map<String, TypeField> byKey = byKey(fields);
        for (int i = 0; i < fields.size(); i++) {
            TypeField field = fields.get(i);
            Condition condition = field.showWhen();
            if (condition == null) {
                continue;
            }
            TypeField parent = byKey.get(condition.fieldKey());
            if (parent == null || parent.key().equals(field.key())) {
                throw bad(field.label() + ": a condição tem de apontar para outro campo deste Tipo.");
            }
            TypeFieldKind parentKind = TypeFieldKind.valueOf(parent.kind());
            if (!CONDITION_KINDS.contains(parentKind)) {
                throw bad(field.label() + ": a condição só pode depender de um campo Sim/Não, Seleção "
                        + "ou Seleção múltipla.");
            }
            List<String> values = condition.values();
            if (parentKind == TypeFieldKind.BOOLEAN) {
                values = values.stream().map(v -> v.toLowerCase(Locale.ROOT)).distinct().toList();
                if (!Set.of("true", "false").containsAll(values)) {
                    throw bad(field.label() + ": a condição sobre \"" + parent.label()
                            + "\" só aceita os valores \"true\" e \"false\".");
                }
                fields.set(i, withCondition(field, new Condition(condition.fieldKey(), values)));
            } else {
                Set<String> optionValues = new HashSet<>();
                parent.options().forEach(option -> optionValues.add(option.value()));
                for (String value : values) {
                    if (!optionValues.contains(value)) {
                        throw bad(field.label() + ": o valor \"" + value + "\" da condição não existe nas opções de \""
                                + parent.label() + "\".");
                    }
                }
            }
        }
        // Cada campo depende no máximo de outro: basta seguir a cadeia para detetar ciclos.
        for (TypeField field : fields) {
            Set<String> visited = new HashSet<>();
            TypeField current = field;
            while (current != null && current.showWhen() != null) {
                if (!visited.add(current.key())) {
                    throw bad("A condição de \"" + field.label() + "\" forma um ciclo entre campos.");
                }
                current = byKey.get(current.showWhen().fieldKey());
            }
        }
    }

    private static TypeField withCondition(TypeField f, Condition condition) {
        return new TypeField(f.key(), f.label(), f.kind(), f.required(), f.helpText(), f.options(), f.unit(),
                f.min(), f.max(), condition, f.sequence(), f.active());
    }

    // ----- Valores (demanda) -----

    /**
     * Valida {@code typeFields} contra os campos do Tipo. Chaves desconhecidas ou de campos inativos são
     * ignoradas; valores vazios não são guardados. Não exige obrigatórios: um rascunho pode ficar incompleto.
     */
    public static Map<String, Object> normalizeValues(PhaseModel type, JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (!node.isObject()) {
            throw bad("typeFields tem de ser um objeto.");
        }
        Map<String, TypeField> active = new HashMap<>();
        definitions(type).stream().filter(TypeFieldRules::isActive).forEach(f -> active.put(f.key(), f));
        node.fields().forEachRemaining(entry -> {
            TypeField field = active.get(entry.getKey());
            if (field == null) {
                return;
            }
            Object value = convert(field, entry.getValue());
            if (value != null) {
                result.put(field.key(), value);
            }
        });
        return result;
    }

    private static Object convert(TypeField field, JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        String label = field.label();
        TypeFieldKind kind = TypeFieldKind.valueOf(field.kind());
        switch (kind) {
            case TEXT, TEXTAREA -> {
                if (!value.isTextual()) {
                    throw bad(label + ": tem de ser texto.");
                }
                return value.textValue().isBlank() ? null : value.textValue();
            }
            case NUMBER, CURRENCY -> {
                if (!value.isNumber()) {
                    throw bad(label + ": tem de ser um número.");
                }
                BigDecimal number = value.decimalValue();
                if (field.min() != null && number.compareTo(field.min()) < 0) {
                    throw bad(label + ": mínimo " + plain(field.min()) + ".");
                }
                if (field.max() != null && number.compareTo(field.max()) > 0) {
                    throw bad(label + ": máximo " + plain(field.max()) + ".");
                }
                return value.numberValue();
            }
            case DATE -> {
                String text = value.isTextual() ? value.textValue().trim() : null;
                if (text == null || text.isEmpty()) {
                    if (text != null) {
                        return null;
                    }
                    throw bad(label + ": data inválida, use AAAA-MM-DD.");
                }
                try {
                    LocalDate.parse(text);
                } catch (DateTimeParseException e) {
                    throw bad(label + ": data inválida, use AAAA-MM-DD.");
                }
                return text;
            }
            case BOOLEAN -> {
                if (!value.isBoolean()) {
                    throw bad(label + ": tem de ser verdadeiro ou falso.");
                }
                return value.booleanValue();
            }
            case SELECT -> {
                if (!value.isTextual()) {
                    throw bad(label + ": tem de ser uma das opções.");
                }
                if (value.textValue().isBlank()) {
                    return null;
                }
                requireOption(field, value.textValue());
                return value.textValue();
            }
            case MULTISELECT, DIRECTIONS -> {
                if (!value.isArray()) {
                    throw bad(label + (kind == TypeFieldKind.DIRECTIONS
                            ? ": tem de ser uma lista de códigos de direção."
                            : ": tem de ser uma lista de opções."));
                }
                List<String> items = new ArrayList<>();
                for (JsonNode item : value) {
                    if (!item.isTextual() || item.textValue().isBlank()) {
                        throw bad(label + ": a lista só pode ter valores de texto não vazios.");
                    }
                    if (kind == TypeFieldKind.MULTISELECT) {
                        requireOption(field, item.textValue());
                    }
                    items.add(item.textValue());
                }
                return items.isEmpty() ? null : items;
            }
            default -> throw new IllegalStateException("Tipo de campo não suportado: " + kind);
        }
    }

    private static void requireOption(TypeField field, String value) {
        boolean known = field.options() != null && field.options().stream().anyMatch(o -> o.value().equals(value));
        if (!known) {
            throw bad(field.label() + ": opção inválida \"" + value + "\".");
        }
    }

    /** Valores para a cópia de uma demanda (mesmo Tipo), no formato do pedido. */
    public static JsonNode toNode(Map<String, Object> values) {
        return values == null || values.isEmpty() ? null : MAPPER.valueToTree(values);
    }

    // ----- Obrigatórios ao sair de Draft -----

    /** Recusa a saída de Draft quando falta um campo obrigatório visível do Tipo da demanda. */
    public static void requireVisibleRequired(Demand demand) {
        PhaseModel type = demand.getType();
        if (type == null) {
            return;
        }
        List<TypeField> fields = definitions(type);
        Map<String, TypeField> byKey = byKey(fields);
        Map<String, Object> values = demand.getTypeFields() == null ? Map.of() : demand.getTypeFields();
        for (TypeField field : fields) {
            if (Boolean.TRUE.equals(field.required()) && isVisible(field, byKey, values, new HashSet<>())
                    && isEmpty(values.get(field.key()))) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "TYPE_FIELD_REQUIRED",
                        "Preencha \"" + field.label() + "\" nos Detalhes de " + type.getName() + ".",
                        Map.of("field", field.key()));
            }
        }
    }

    /**
     * Ativo e sem condição, ou com o valor atual do campo da condição em {@code showWhen.values} (numa
     * lista basta um elemento; num booleano compara "true"/"false") e esse campo também visível.
     */
    static boolean isVisible(TypeField field, Map<String, TypeField> byKey, Map<String, Object> values,
            Set<String> visiting) {
        if (!isActive(field)) {
            return false;
        }
        Condition condition = field.showWhen();
        if (condition == null) {
            return true;
        }
        if (!visiting.add(field.key())) {
            return false;
        }
        TypeField parent = byKey.get(condition.fieldKey());
        if (parent == null || !isVisible(parent, byKey, values, visiting)) {
            return false;
        }
        Object current = values.get(parent.key());
        if (current instanceof Collection<?> list) {
            return list.stream().anyMatch(item -> condition.values().contains(String.valueOf(item)));
        }
        return current != null && condition.values().contains(String.valueOf(current));
    }

    public static List<TypeField> definitions(PhaseModel type) {
        return type == null || type.getFields() == null ? List.of() : type.getFields();
    }

    private static boolean isActive(TypeField field) {
        return !Boolean.FALSE.equals(field.active());
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || (value instanceof String text && text.isBlank())
                || (value instanceof Collection<?> list && list.isEmpty());
    }

    private static Map<String, TypeField> byKey(List<TypeField> fields) {
        Map<String, TypeField> byKey = new HashMap<>();
        fields.forEach(f -> byKey.put(f.key(), f));
        return byKey;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BadRequestException bad(String message) {
        return new BadRequestException(message);
    }
}
