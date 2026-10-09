package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.ProjectProfileOrigin;

import java.util.List;
import java.util.UUID;

/**
 * Perfil previsto do projeto com as pessoas já associadas. {@code missing} = quantas faltam para chegar
 * a {@code requiredQuantity} (nunca negativo). {@code origin}: {@code DEMAND} (herdado) ou
 * {@code PROJECT} (acrescentado no projeto).
 */
public record ProjectProfileResponse(UUID id, UUID profileId, String profileName, String directionCode,
        String directionName, Integer requiredQuantity, int assignedCount, int missing, List<String> phases,
        List<ProjectTeamMemberResponse> members, ProjectProfileOrigin origin) {
}
