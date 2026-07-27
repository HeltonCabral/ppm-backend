package cvt.cv.ppmbackend.dto;

import jakarta.validation.constraints.*;

public record LookupValueCreateRequest(
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 150) String label,
        Integer sortOrder) {
}
