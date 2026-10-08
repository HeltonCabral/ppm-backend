package cvt.cv.ppmbackend.enums;

/** Estado de utilização de capacidade de um perfil, período ou Demanda. */
public enum CapacityStatus {
    OK,
    CONDITIONED,
    RESTRICTED,
    CRITICAL,
    NOT_CONFIGURED,
    /** A capacidade ainda não pode ser calculada (ex.: sem início previsto/duração da Demanda). */
    NOT_CALCULATED;

    /** Severidade crescente. NOT_CONFIGURED é o pior porque impede avaliação segura. */
    public int severity() {
        return switch (this) {
            case NOT_CALCULATED -> -1;
            case OK -> 0;
            case CONDITIONED -> 1;
            case RESTRICTED -> 2;
            case CRITICAL -> 3;
            case NOT_CONFIGURED -> 4;
        };
    }

    public static CapacityStatus worst(CapacityStatus a, CapacityStatus b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.severity() >= b.severity() ? a : b;
    }
}
