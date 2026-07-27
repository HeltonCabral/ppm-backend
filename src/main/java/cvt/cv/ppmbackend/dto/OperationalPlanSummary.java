package cvt.cv.ppmbackend.dto;

import java.math.BigDecimal;
import java.util.Map;

public record OperationalPlanSummary(long count, BigDecimal budget, BigDecimal consumed, long atRisk,
        Map<String, Long> byStatus) {
}
