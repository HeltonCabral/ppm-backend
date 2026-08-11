package cvt.cv.ppmbackend.dto;

import java.math.BigDecimal;

public record PreScoreResponse(
        BigDecimal preScore,
        String preScoreClassification) {
}
