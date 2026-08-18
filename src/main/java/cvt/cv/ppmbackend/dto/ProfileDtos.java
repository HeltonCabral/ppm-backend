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
            @NotNull ProfileCategory category,
            @Size(max = 10000) String description,
            @NotNull @PositiveOrZero Integer availableCapacity) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull ProfileCategory category,
            @Size(max = 10000) String description,
            @NotNull @PositiveOrZero Integer availableCapacity) {
    }

    public record Response(
            UUID id,
            String name,
            ProfileCategory category,
            String description,
            Integer availableCapacity,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        public static Response from(Profile profile) {
            return new Response(profile.getId(), profile.getName(), profile.getCategory(), profile.getDescription(),
                    profile.getAvailableCapacity(), profile.isActive(), profile.getCreatedAt(), profile.getUpdatedAt());
        }
    }
}
