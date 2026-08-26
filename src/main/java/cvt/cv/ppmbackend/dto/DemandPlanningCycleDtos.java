package cvt.cv.ppmbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import cvt.cv.ppmbackend.dto.DemandDtos.CommitteeSummary;
import cvt.cv.ppmbackend.dto.DemandDtos.OperationalPlanSummary;
import cvt.cv.ppmbackend.dto.DemandDtos.ProgramSummary;
import cvt.cv.ppmbackend.dto.DemandDtos.StrategicElementSummary;
import cvt.cv.ppmbackend.dto.DemandDtos.StrategicObjectiveSummary;
import cvt.cv.ppmbackend.dto.DemandDtos.StrategicPillarSummary;
import cvt.cv.ppmbackend.dto.DemandDtos.StrategicPlanSummary;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class DemandPlanningCycleDtos {
    private DemandPlanningCycleDtos() {
    }

    public record TransitionRequest(@NotNull DemandPlanningStatus status, @Size(max = 4000) String reason) {
    }

    public record EnrichmentRequest(
            UUID strategicPillarId,
            UUID strategicElementId,
            UUID strategicObjectiveId,
            UUID programId,
            @Size(max = 40) String capacityStatus,
            @Size(max = 40) String riskStatus) {
    }

    public record Response(
            UUID id,
            UUID demandId,
            StrategicPlanSummary strategicPlan,
            OperationalPlanSummary operationalPlan,
            StrategicPillarSummary strategicPillar,
            StrategicElementSummary strategicElement,
            StrategicObjectiveSummary strategicObjective,
            ProgramSummary program,
            DemandPlanningStatus status,
            CommitteeSummary responsibleCommittee,
            boolean inStrategicCommittee,
            Instant strategicCommitteeAt,
            BigDecimal scoreTotal,
            String scoreStatus,
            Instant scoreCalculatedAt,
            Instant scoreInvalidatedAt,
            String scoreInvalidationReason,
            JsonNode previousScoreSnapshot,
            Integer portfolioRank,
            Integer directionRank,
            Integer committeeRank,
            String capacityStatus,
            String riskStatus,
            String approvalType,
            String committeeDecision,
            String rejectionReason,
            Instant decisionAt,
            String decisionBy,
            String reprioritizationReason,
            String reprioritizationJustification,
            Instant reprioritizedAt,
            String reprioritizedBy,
            boolean carriedOver,
            UUID previousCycleId,
            Instant cycleEntryAt,
            UUID convertedProjectId,
            UUID convertedProgramId,
            Instant convertedAt,
            String convertedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    public record PlanningHistoryItem(
            Integer year,
            String strategicPlan,
            String operationalPlan,
            DemandPlanningStatus status,
            BigDecimal score,
            Integer portfolioRank,
            String committeeDecision,
            boolean carriedOver) {
    }
}
