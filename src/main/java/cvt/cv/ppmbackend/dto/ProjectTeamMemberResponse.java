package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.entity.ProjectTeamMember;
import cvt.cv.ppmbackend.enums.MemberType;

import java.util.UUID;

public record ProjectTeamMemberResponse(UUID id, UUID projectId, String code, String directionName,
        String directionCode, String areaName, String areaCode, String name, String email, String role, MemberType type) {

    public static ProjectTeamMemberResponse from(ProjectTeamMember m) {
        return new ProjectTeamMemberResponse(m.getId(), m.getProject().getId(), m.getCode(), m.getDirectionName(),
                m.getDirectionCode(), m.getAreaName(), m.getAreaCode(), m.getName(), m.getEmail(), m.getRole(),
                m.getType());
    }
}
