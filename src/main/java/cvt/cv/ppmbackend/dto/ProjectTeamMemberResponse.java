package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.MemberType;
import java.util.UUID;

public record ProjectTeamMemberResponse(UUID id, UUID projectId, String code, String directionName,
        String directionCode, String areaName, String areaCode, String name, String email, String role, MemberType type,
        UUID projectProfileId, String profileName) {

    public static ProjectTeamMemberResponse from(ProjectTeamMember m) {
        ProjectProfile profile = m.getProjectProfile();
        return new ProjectTeamMemberResponse(m.getId(), m.getProject().getId(), m.getCode(), m.getDirectionName(),
                m.getDirectionCode(), m.getAreaName(), m.getAreaCode(), m.getName(), m.getEmail(), m.getRole(),
                m.getType(), profile != null ? profile.getId() : null, profile != null ? profile.getProfileName() : null);
    }
}
