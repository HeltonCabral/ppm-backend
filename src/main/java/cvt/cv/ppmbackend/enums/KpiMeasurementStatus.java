package cvt.cv.ppmbackend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum KpiMeasurementStatus {
    NO_ALVO("No Alvo"),
    ATENCAO("Atenção"),
    FORA_DO_ALVO("Fora do Alvo");

    private final String label;

    KpiMeasurementStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static KpiMeasurementStatus of(String value) {
        if (value == null)
            return null;
        for (KpiMeasurementStatus s : values())
            if (s.name().equalsIgnoreCase(value) || s.label.equalsIgnoreCase(value))
                return s;
        throw new IllegalArgumentException("Status de KPI inválido: " + value);
    }
}
