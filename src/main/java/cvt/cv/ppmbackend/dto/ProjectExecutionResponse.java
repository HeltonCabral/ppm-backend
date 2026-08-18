package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.ProjectExecution;
import cvt.cv.ppmbackend.enums.ExecutiveStatus;
import cvt.cv.ppmbackend.enums.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectExecutionResponse(UUID id, Integer progress, BigDecimal consumedBudget,
        LocalDate plannedStartDate, LocalDate actualStartDate, LocalDate plannedEndDate, LocalDate actualEndDate,
        ExecutiveStatus scheduleStatus, ExecutiveStatus costStatus, ExecutiveStatus riskStatus,
        ExecutiveStatus valueStatus, RiskLevel risk, String delayReasons, String executionNotes,
        Instant lastUpdatedAt, String lastUpdatedBy, Instant createdAt, Instant updatedAt) {
    public static ProjectExecutionResponse from(ProjectExecution e) {
        return e == null ? null : new ProjectExecutionResponse(e.getId(), e.getProgress(), e.getConsumedBudget(),
                e.getPlannedStartDate(), e.getActualStartDate(), e.getPlannedEndDate(), e.getActualEndDate(),
                e.getScheduleStatus(), e.getCostStatus(), e.getRiskStatus(), e.getValueStatus(), e.getRisk(),
                e.getDelayReasons(), e.getExecutionNotes(), e.getLastUpdatedAt(), e.getLastUpdatedBy(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
