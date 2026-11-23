package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.CommitteeDecisionDtos.*;
import cvt.cv.ppmbackend.service.CommitteeDecisionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/committee-decisions")
public class CommitteeDecisionController {

    private final CommitteeDecisionService committeeDecisionService;

    public CommitteeDecisionController(CommitteeDecisionService committeeDecisionService) {
        this.committeeDecisionService = committeeDecisionService;
    }


    @GetMapping("/strategic-plans/{strategicPlanId}/dashboard")
    public DecisionDashboardResponse dashboard(@PathVariable UUID strategicPlanId) {
        return committeeDecisionService.dashboard(strategicPlanId);
    }
}
