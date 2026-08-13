package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.StrategicObjective;
import cvt.cv.ppmbackend.entity.Supplier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProgramResponse(
        UUID id,
        String name,
        String description,
        String programManager,
        BigDecimal estimatedBudget,
        int totalProjects,
        double averageProgress,
        List<StrategicObjectiveRef> strategicObjectives,
        List<SupplierRef> suppliers,
        List<ProjectResponse> projects) {

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

    public static ProgramResponse from(Program p, List<Project> projects, double averageProgress, Set<StrategicObjectiveRef> aggregatedObjectives) {
        return new ProgramResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getProgramManager(),
                p.getEstimatedBudget(),
                projects.size(),
                averageProgress,
                aggregatedObjectives == null ? List.of() : aggregatedObjectives.stream().toList(),
                p.getSuppliers() == null ? List.of()
                        : p.getSuppliers().stream().map(SupplierRef::from).toList(),
                projects.stream().map(ProjectResponse::from).toList());
    }

    public static ProgramResponse from(Program p, List<Project> projects, double averageProgress) {
        return new ProgramResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getProgramManager(),
                p.getEstimatedBudget(),
                projects.size(),
                averageProgress,
                p.getStrategicObjectives() == null ? List.of()
                        : p.getStrategicObjectives().stream().map(StrategicObjectiveRef::from).toList(),
                p.getSuppliers() == null ? List.of()
                        : p.getSuppliers().stream().map(SupplierRef::from).toList(),
                projects.stream().map(ProjectResponse::from).toList());
    }

    // Overload for simple conversion without projects data
    public static ProgramResponse from(Program p) {
        return new ProgramResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getProgramManager(),
                p.getEstimatedBudget(),
                0,
                0.0,
                p.getStrategicObjectives() == null ? List.of()
                        : p.getStrategicObjectives().stream().map(StrategicObjectiveRef::from).toList(),
                p.getSuppliers() == null ? List.of()
                        : p.getSuppliers().stream().map(SupplierRef::from).toList(),
                List.of());
    }
}
