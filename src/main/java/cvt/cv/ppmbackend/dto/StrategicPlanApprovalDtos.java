package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import java.util.List;
import java.util.UUID;

public interface StrategicPlanApprovalDtos {

    record ConditionalApprovalResponse(
            UUID strategicPlanId,
            StrategicPlanStatus status,
            int convertedToProjects,
            int convertedToPrograms,
            int projectsCreated,
            int programsCreated,
            int movedToBacklog,
            int ignoredDemands,
            List<String> errors
    ) {}

    record FinalApprovalResponse(
            UUID strategicPlanId,
            StrategicPlanStatus status,
            int alreadyConverted,
            int movedToBacklog,
            List<String> errors
    ) {}
}
