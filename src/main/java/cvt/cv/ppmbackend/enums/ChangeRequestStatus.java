package cvt.cv.ppmbackend.enums;

/** Estado de um pedido de alteração. Só PENDING pode ser decidido ou cancelado. */
public enum ChangeRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
