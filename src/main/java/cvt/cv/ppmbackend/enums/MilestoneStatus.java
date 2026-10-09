package cvt.cv.ppmbackend.enums;

/** Estado de um marco. "Atrasado" não é um estado: é PENDING com dueDate no passado. */
public enum MilestoneStatus {
    PENDING,
    DONE,
    CANCELLED
}
