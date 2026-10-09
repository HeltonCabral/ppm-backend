package cvt.cv.ppmbackend.enums;

/** Tipo de um campo específico de um Tipo de Demanda, e por isso do valor guardado na demanda. */
public enum TypeFieldKind {
    /** string */
    TEXT,
    /** string */
    TEXTAREA,
    /** número; usa unit, min e max */
    NUMBER,
    /** número (escudos); usa min e max */
    CURRENCY,
    /** string YYYY-MM-DD */
    DATE,
    /** true / false */
    BOOLEAN,
    /** string, um dos options[].value */
    SELECT,
    /** lista de strings, cada uma um options[].value */
    MULTISELECT,
    /** lista de códigos de direção do hub */
    DIRECTIONS
}
