package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.MemberType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectTeamMemberRequest(
        @Size(max = 50) String code,
        @Size(max = 200) String directionName,
        @Size(max = 100) String directionCode,
        @Size(max = 200) String areaName,
        @Size(max = 100) String areaCode,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String email,
        @Size(max = 100) String role,
        @NotNull MemberType type) {
}
