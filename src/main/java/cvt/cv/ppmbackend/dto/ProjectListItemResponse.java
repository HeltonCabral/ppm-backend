package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Linha da lista de projetos. {@code lastReportAt}/{@code lastReportStatus}/{@code needsSupport} permitem
 * sinalizar projetos sem relatório há mais de 30 dias ou a pedir apoio à PMO.
 */
public record ProjectListItemResponse(UUID id, String code, String name, String description, String origin,
        String sourceDemandCode, Boolean createdFromConditionalPlanApproval, Boolean createdFromDemandBacklog,
        String programName, String directionCode, String directionName, String areaCode,
        String areaName, String domain, String supplierName, BigDecimal budget, Integer executionRank, String status,
        String projectPhase, String projectManager, String extraPlanJustification, ProjectExecutionResponse execution,
        String currentPhaseName, Instant lastReportAt, ExecutiveStatus lastReportStatus, Boolean needsSupport,
        String projectManagerUsername) {
    public static ProjectListItemResponse from(Project p) {
        ProjectResponse r = ProjectResponse.from(p);
        return new ProjectListItemResponse(r.id(), r.code(), r.name(), r.description(), r.origin(),
                r.sourceDemandCode(), r.createdFromConditionalPlanApproval(), r.createdFromDemandBacklog(),
                r.programName(), r.directionCode(), r.directionName(), r.areaCode(), r.areaName(),
                r.domain(), r.supplierName(), r.budget(), r.executionRank(), r.status(), r.projectPhase(),
                r.projectManager(), r.extraPlanJustification(), r.execution(), r.currentPhaseName(),
                r.lastReportAt(), r.lastReportStatus(), r.needsSupport(), r.projectManagerUsername());
    }
}
