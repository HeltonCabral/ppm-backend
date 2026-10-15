package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.MemberType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code warnings}: avisos de sobrealocação ou de período fora do projeto, só nas respostas de gravação. */
public record ProjectTeamMemberResponse(UUID id, UUID projectId, String code, String directionName,
        String directionCode, String areaName, String areaCode, String name, String email, String role, MemberType type,
        UUID projectProfileId, String profileName, Integer allocationPercentage, LocalDate startDate,
        LocalDate endDate, List<String> warnings) {

    public static ProjectTeamMemberResponse from(ProjectTeamMember m) {
        return from(m, List.of());
    }

    public static ProjectTeamMemberResponse from(ProjectTeamMember m, List<String> warnings) {
        ProjectProfile profile = m.getProjectProfile();
        return new ProjectTeamMemberResponse(m.getId(), m.getProject().getId(), m.getCode(), m.getDirectionName(),
                m.getDirectionCode(), m.getAreaName(), m.getAreaCode(), m.getName(), m.getEmail(), m.getRole(),
                m.getType(), profile != null ? profile.getId() : null, profile != null ? profile.getProfileName() : null,
                m.getAllocationPercentage() == null ? 100 : m.getAllocationPercentage(), m.getStartDate(),
                m.getEndDate(), warnings);
    }
}
