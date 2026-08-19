package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.enums.ProfileCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ProfileDtos {
    private ProfileDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 10000) String description,
            String directionCode,
            String directionName,
            @NotNull @PositiveOrZero Integer availableCapacity,
            @NotNull @PositiveOrZero Integer simultaneousDemandCapacity) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 150) String name,
            String directionCode,
            String directionName,
            @Size(max = 10000) String description,
            @NotNull @PositiveOrZero Integer availableCapacity,
         @NotNull @PositiveOrZero Integer simultaneousDemandCapacity) {
    }

    public record Response(
            UUID id,
            String name,
            String directionCode,
            String directionName,
            String description,
            Integer availableCapacity,
            Integer simultaneousDemandCapacity,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        public static Response from(Profile profile) {
            return new Response(profile.getId(), profile.getName(),profile.getDirectionCode(),profile.getDirectionName(), profile.getDescription(),
                    profile.getAvailableCapacity(),profile.getSimultaneousDemandCapacity(), profile.isActive(), profile.getCreatedAt(), profile.getUpdatedAt());
        }
    }
}
