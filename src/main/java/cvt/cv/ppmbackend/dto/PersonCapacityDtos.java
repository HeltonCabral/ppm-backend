package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.CapacityReason;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Capacidade individual das pessoas (Administração › Capacidade das Pessoas). */
public final class PersonCapacityDtos {
    private PersonCapacityDtos() {
    }

    public record Request(String userName, String fullName, Integer capacityPercentage, Integer maxConcurrent,
            CapacityReason reason,
            LocalDate validFrom, LocalDate validUntil, String notes) {
        public Request(String userName, String fullName, Integer capacityPercentage, CapacityReason reason,
                LocalDate validFrom, LocalDate validUntil, String notes) {
            this(userName, fullName, capacityPercentage, null, reason, validFrom, validUntil, notes);
        }
    }

    public record Response(UUID id, String userName, String fullName, Integer capacityPercentage, Integer maxConcurrent,
            CapacityReason reason, LocalDate validFrom, LocalDate validUntil, String notes, Instant createdAt,
            String createdBy, Instant updatedAt, String updatedBy) {
    }
}
