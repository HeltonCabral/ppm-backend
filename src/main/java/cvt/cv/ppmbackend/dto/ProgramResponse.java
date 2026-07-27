package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import cvt.cv.ppmbackend.entity.Supplier;
import java.util.List;
import java.util.UUID;

public record ProgramResponse(
        UUID id,
        String name,
        String description,
        String programManager,
        List<StrategicObjectiveRef> strategicObjectives,
        List<SupplierRef> suppliers) {

    public record StrategicObjectiveRef(UUID id, String name) {
        public static StrategicObjectiveRef from(StrategicObjective objective) {
            return new StrategicObjectiveRef(objective.getId(), objective.getName());
        }
    }

    public record SupplierRef(UUID id, String name) {
        public static SupplierRef from(Supplier s) {
            return new SupplierRef(s.getId(), s.getName());
        }
    }

    public static ProgramResponse from(Program p) {
        return new ProgramResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getProgramManager(),
                p.getStrategicObjectives() == null ? List.of()
                        : p.getStrategicObjectives().stream().map(StrategicObjectiveRef::from).toList(),
                p.getSuppliers() == null ? List.of()
                        : p.getSuppliers().stream().map(SupplierRef::from).toList());
    }
}
