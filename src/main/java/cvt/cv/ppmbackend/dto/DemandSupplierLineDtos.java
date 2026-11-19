package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.SupplierCostMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public final class DemandSupplierLineDtos {
    private DemandSupplierLineDtos() {
    }

    /** {@code quantity} null keeps a supplier whose hours or value are not known yet. */
    public record Input(
            @NotNull UUID supplierId,
            @NotNull SupplierCostMode mode,
            @Positive BigDecimal quantity) {
    }

    public record Response(
            UUID id,
            UUID supplierId,
            String supplierName,
            SupplierCostMode mode,
            BigDecimal quantity) {
    }
}
