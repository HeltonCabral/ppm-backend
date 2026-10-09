package cvt.cv.ppmbackend.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Campo específico de um Tipo de Demanda (modelo de fases). É o formato do pedido, da resposta e do
 * que fica guardado na coluna JSON {@code phase_models.fields}; {@link cvt.cv.ppmbackend.service.TypeFieldRules}
 * valida-o e normaliza-o antes de gravar.
 *
 * @param key       nome sob o qual as demandas guardam o valor; nunca muda depois de criado
 * @param kind      um dos {@link cvt.cv.ppmbackend.enums.TypeFieldKind}
 * @param showWhen  condição de visibilidade; {@code null} = sempre visível
 */
public record TypeField(
        String key,
        String label,
        String kind,
        Boolean required,
        String helpText,
        List<Option> options,
        String unit,
        BigDecimal min,
        BigDecimal max,
        Condition showWhen,
        Integer sequence,
        Boolean active) {

    public record Option(String value, String label) {
    }

    /** Visível quando o valor atual de {@code fieldKey} está em {@code values}. */
    public record Condition(String fieldKey, List<String> values) {
    }
}
