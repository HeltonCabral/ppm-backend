package cvt.cv.ppmbackend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StrategicPlanStatus {
    DRAFT("Draft"),
    IN_REVIEW("Em Revisao"),
    CONDITIONALLY_APPROVED("Aprovado Condicionalmente"),
    APPROVED("Aprovado"),
    ACTIVE("Ativo"),
    REPLACED("Substituido"),
    CLOSED("Encerrado");

    private final String label;

    StrategicPlanStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static StrategicPlanStatus of(String value) {
        if (value == null)
            return null;
        for (StrategicPlanStatus s : values())
            if (s.name().equalsIgnoreCase(value) || s.label.equalsIgnoreCase(value))
                return s;
        throw new IllegalArgumentException("Status de plano estratégico inválido: " + value);
    }
}
