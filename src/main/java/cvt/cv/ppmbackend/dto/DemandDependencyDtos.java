package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.DemandDependencyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class DemandDependencyDtos {
    private DemandDependencyDtos() {
    }

    public record CreateRequest(
            @NotNull UUID dependsOnDemandId,
            @NotNull DemandDependencyType type,
            @Size(max = 10000) String description) {
    }

    public record Response(
            UUID id,
            UUID dependencyId,
            UUID dependsOnDemandId,
            String code,
            String title,
            DemandDependencyType type,
            String description) {
    }
}
