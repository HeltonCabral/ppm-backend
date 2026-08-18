package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Project;
import java.math.BigDecimal;
import java.util.UUID;

public record ProjectResponse(UUID id, String code, String name, String description, String origin,
        String sourceDemandCode, String programName, String directionCode, String directionName, String areaCode,
        String areaName, String domain, String supplierName, BigDecimal budget, Integer executionRank, String status,
        String projectPhase, String projectManager, String extraPlanJustification, ProjectExecutionResponse execution) {

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), text(p.getOrigin()),
                p.getSourceDemand() != null ? p.getSourceDemand().getCode() : null,
                p.getProgram() != null ? p.getProgram().getName() : null, p.getDirectionCode(), p.getDirectionName(),
                p.getAreaCode(), p.getAreaName(), p.getDomain() != null ? p.getDomain().getLabel() : null,
                p.getMainSupplier() != null ? p.getMainSupplier().getName() : null, p.getBudget(), p.getExecutionRank(),
                text(p.getStatus()), p.getProjectPhase() != null ? p.getProjectPhase().getLabel() : null,
                p.getProjectManager(), p.getExtraPlanJustification(), ProjectExecutionResponse.from(p.getExecution()));
    }

    private static String text(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
