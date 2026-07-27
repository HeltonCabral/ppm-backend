package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Kpi;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import cvt.cv.ppmbackend.entity.StrategicObjectiveAnnualTarget;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StrategicObjectiveResponse(
        UUID id,
        String name,
        String description,
        Integer fiscalYear,
        Integer startYear,
        Integer endYear,
        String perspective,
        UUID strategicElementId,
        String strategicElementName,
        UUID strategicPillarId,
        String strategicPillarName,
        UUID strategicPlanId,
        List<AnnualTargetResponse> annualTargets,
        List<KpiResponse> kpis) {

    public record AnnualTargetResponse(UUID id, Integer year, String targetLabel, BigDecimal targetValue,
            BigDecimal weight) {
        public static AnnualTargetResponse from(StrategicObjectiveAnnualTarget t) {
            return new AnnualTargetResponse(t.getId(), t.getYear(), t.getTargetLabel(), t.getTargetValue(),
                    t.getWeight());
        }
    }

    public record KpiResponse(UUID id, String name, String target, Double current, Double goal,
            List<KpiMeasurementResponse> measurements) {
        public static KpiResponse from(Kpi k) {
            return new KpiResponse(k.getId(), k.getName(), k.getTarget(), k.getCurrent(), k.getGoal(),
                    k.getMeasurements() == null ? List.of()
                            : k.getMeasurements().stream().map(KpiMeasurementResponse::from).toList());
        }
    }

    public record KpiMeasurementResponse(UUID id, Integer year, BigDecimal current, BigDecimal goal) {
        public static KpiMeasurementResponse from(cvt.cv.ppmbackend.entity.KpiMeasurement m) {
            return new KpiMeasurementResponse(m.getId(), m.getYear(), m.getCurrent(), m.getGoal());
        }
    }

    public static StrategicObjectiveResponse from(StrategicObjective o) {
        var el = o.getStrategicElement();
        var pillar = el != null ? el.getStrategicPillar() : null;
        return new StrategicObjectiveResponse(
                o.getId(),
                o.getName(),
                o.getDescription(),
                o.getFiscalYear(),
                o.getStartYear(),
                o.getEndYear(),
                o.getPerspective(),
                el != null ? el.getId() : null,
                el != null ? el.getName() : null,
                pillar != null ? pillar.getId() : null,
                pillar != null ? pillar.getName() : null,
                o.getStrategicPlanId(),
                o.getAnnualTargets() == null ? List.of()
                        : o.getAnnualTargets().stream().map(AnnualTargetResponse::from).toList(),
                o.getKpis() == null ? List.of()
                        : o.getKpis().stream().map(KpiResponse::from).toList());
    }
}
