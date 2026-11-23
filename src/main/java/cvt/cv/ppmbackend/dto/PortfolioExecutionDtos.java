package cvt.cv.ppmbackend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class PortfolioExecutionDtos {
    private PortfolioExecutionDtos() {
    }

    public record Group(UUID id, String code, String name, long projects, long green, long yellow, long red,
            long noReport, double avgProgress, BigDecimal budget, BigDecimal consumed, long slipped, long stale) {
    }

    public record Response(List<Group> byDirection, List<Group> byProgram) {
    }
}
