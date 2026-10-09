package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.UserDirectionSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos de acesso por direção: atribuições, traduções de departamento e {@code /me/access}. */
public final class AccessDtos {
    private AccessDtos() {
    }

    public record UserDirectionInput(
            String userName,
            String fullName,
            String directionCode,
            String directionName,
            LocalDate validUntil,
            UserDirectionSource source,
            String adDepartment) {
    }

    public record UserDirectionResponse(
            UUID id,
            String userName,
            String fullName,
            String directionCode,
            String directionName,
            LocalDate validUntil,
            UserDirectionSource source,
            String adDepartment,
            Instant createdAt,
            String createdBy,
            Instant updatedAt) {
    }

    public record BulkResult(int created, int skipped, List<String> errors) {
    }

    public record DepartmentMappingInput(String departmentKey, String directionCode, String directionName) {
    }

    public record DepartmentMappingResponse(
            UUID id,
            String departmentKey,
            String directionCode,
            String directionName,
            Instant createdAt,
            String createdBy) {
    }

    public record DirectionRef(String code, String name) {
    }

    public record MeAccessResponse(
            String userName,
            List<DirectionRef> directions,
            List<String> permissions,
            boolean viewAll,
            boolean editAll,
            boolean filterEnabled,
            boolean projectFilterEnabled) {
    }
}
