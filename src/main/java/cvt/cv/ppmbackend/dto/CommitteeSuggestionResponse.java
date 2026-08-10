package cvt.cv.ppmbackend.dto;

import java.util.List;
import java.util.UUID;

public record CommitteeSuggestionResponse(
        UUID suggestedCommitteeId,
        String suggestedCommitteeName,
        String suggestedCommitteeNameKey,
        int score,
        List<String> reasons,
        List<CommitteeAlternativeResponse> alternatives) {
}
