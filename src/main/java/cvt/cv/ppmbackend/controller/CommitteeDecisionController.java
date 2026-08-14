package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.CommitteeDecisionDtos.*;
import cvt.cv.ppmbackend.service.CommitteeDecisionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/committee-decisions")
public class CommitteeDecisionController {

    private final CommitteeDecisionService committeeDecisionService;

    public CommitteeDecisionController(CommitteeDecisionService committeeDecisionService) {
        this.committeeDecisionService = committeeDecisionService;
    }

    @PostMapping("/bulk-deliberation")
    public ResponseEntity<BulkDeliberationResponse> bulkDeliberation(
            @Valid @RequestBody BulkDeliberationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String actorId = userId != null && !userId.isBlank() ? userId : "system";
        BulkDeliberationResponse response = committeeDecisionService.deliberate(request, actorId);
        
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
