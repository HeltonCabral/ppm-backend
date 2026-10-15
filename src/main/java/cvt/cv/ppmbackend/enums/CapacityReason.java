package cvt.cv.ppmbackend.enums;

/** Porque é que uma pessoa não dá 100% do tempo a projetos. */
public enum CapacityReason {
    PART_TIME,
    OPERATIONS,
    MANAGEMENT,
    TRAINING,
    LEAVE,
    OTHER;

    public String label() {
        return switch (this) {
            case PART_TIME -> "tempo parcial";
            case OPERATIONS -> "funções operacionais";
            case MANAGEMENT -> "chefia";
            case TRAINING -> "formação";
            case LEAVE -> "ausência";
            case OTHER -> "outro motivo";
        };
    }
}
