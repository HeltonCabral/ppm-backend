package cvt.cv.ppmbackend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OperationalPlanStatus {
    DRAFT("Draft"),
    APROVADO("Aprovado"),
    EM_EXECUCAO("Em Execução"),
    FECHADO("Fechado");

    private final String label;

    OperationalPlanStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static OperationalPlanStatus of(String value) {
        if (value == null)
            return null;
        for (OperationalPlanStatus s : values())
            if (s.name().equalsIgnoreCase(value) || s.label.equalsIgnoreCase(value))
                return s;
        throw new IllegalArgumentException("Status de plano operacional inválido: " + value);
    }
}
