package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Program;
import cvt.cv.ppmbackend.entity.Project;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProgramCardResponse(
        UUID id,
        String name,
        String description,
        String programManager,
        BigDecimal estimatedBudget,
        int totalProjects,
        double averageProgress,
        List<ProjectResponse> projects) {

    public static ProgramCardResponse from(Program p, List<Project> projects, double averageProgress) {
        return new ProgramCardResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getProgramManager(),
                p.getEstimatedBudget(),
                projects.size(),
                averageProgress,
                projects.stream().map(ProjectResponse::from).toList());
    }
}
