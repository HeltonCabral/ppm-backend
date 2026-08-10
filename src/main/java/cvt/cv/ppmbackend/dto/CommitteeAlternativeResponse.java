package cvt.cv.ppmbackend.dto;

import java.util.List;
import java.util.UUID;

public record CommitteeAlternativeResponse(
        UUID committeeId,
        String committeeName,
        String committeeNameKey,
        int score,
        List<String> reasons) {
}
