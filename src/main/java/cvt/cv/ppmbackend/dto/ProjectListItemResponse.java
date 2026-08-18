package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Project;
import java.math.BigDecimal;
import java.util.UUID;

public record ProjectListItemResponse(UUID id, String code, String name, String description, String origin,
        String sourceDemandCode, String programName, String directionCode, String directionName, String areaCode,
        String areaName, String domain, String supplierName, BigDecimal budget, Integer executionRank, String status,
        String projectPhase, String projectManager, String extraPlanJustification, ProjectExecutionResponse execution) {
    public static ProjectListItemResponse from(Project p) {
        ProjectResponse r = ProjectResponse.from(p);
        return new ProjectListItemResponse(r.id(), r.code(), r.name(), r.description(), r.origin(),
                r.sourceDemandCode(), r.programName(), r.directionCode(), r.directionName(), r.areaCode(), r.areaName(),
                r.domain(), r.supplierName(), r.budget(), r.executionRank(), r.status(), r.projectPhase(),
                r.projectManager(), r.extraPlanJustification(), r.execution());
    }
}
