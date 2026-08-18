package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementInput;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class DemandProfileRequirementService {
    private static final Set<Integer> ALLOWED_PERCENTAGES = Set.of(25, 50, 75, 100);

    private final DemandProfileRequirementRepository requirements;
    private final ProfileRepository profiles;

    public DemandProfileRequirementService(DemandProfileRequirementRepository requirements,
            ProfileRepository profiles) {
        this.requirements = requirements;
        this.profiles = profiles;
    }

    public void sync(Demand demand, List<DemandProfileRequirementInput> inputs) {
        if (inputs == null) return;

        List<DemandProfileRequirement> current = requirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId());
        Map<UUID, DemandProfileRequirement> currentByProfile = current.stream()
                .collect(Collectors.toMap(requirement -> requirement.getProfile().getId(), Function.identity()));
        Set<UUID> requestedProfiles = new HashSet<>();
        List<DemandProfileRequirement> updated = new ArrayList<>();

        for (DemandProfileRequirementInput input : inputs) {
            validate(input);
            if (!requestedProfiles.add(input.profileId())) {
                throw new BadRequestException("O mesmo perfil não pode ser repetido na Demanda: " + input.profileId());
            }

            Profile profile = profiles.findById(input.profileId())
                    .orElseThrow(() -> new BadRequestException("Perfil não encontrado: " + input.profileId()));
            DemandProfileRequirement requirement = currentByProfile.get(input.profileId());
            if (requirement == null) {
                if (!profile.isActive()) {
                    throw new BadRequestException("Perfis inativos não podem ser associados a novas Demandas: "
                            + profile.getName());
                }
                requirement = new DemandProfileRequirement();
                requirement.setDemand(demand);
                requirement.setProfile(profile);
            }
            requirement.setRequiredQuantity(input.requiredQuantity());
            requirement.setAllocationPercentage(input.allocationPercentage());
            updated.add(requirement);
        }

        List<DemandProfileRequirement> removed = current.stream()
                .filter(requirement -> !requestedProfiles.contains(requirement.getProfile().getId()))
                .toList();
        requirements.deleteAll(removed);
        requirements.saveAll(updated);
    }

    @Transactional(readOnly = true)
    public List<DemandProfileRequirementResponse> list(UUID demandId) {
        return requirements.findByDemand_IdOrderByProfile_NameAsc(demandId).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public RequirementSummary summary(UUID demandId) {
        List<DemandProfileRequirement> items = requirements.findByDemand_IdOrderByProfile_NameAsc(demandId);
        return new RequirementSummary(items.size(), items.stream()
                .mapToInt(DemandProfileRequirement::getRequiredQuantity)
                .sum());
    }

    private void validate(DemandProfileRequirementInput input) {
        if (input == null || input.profileId() == null) {
            throw new BadRequestException("profileId é obrigatório");
        }
        if (input.requiredQuantity() == null || input.requiredQuantity() <= 0) {
            throw new BadRequestException("requiredQuantity deve ser maior que zero");
        }
        if (input.allocationPercentage() == null || !ALLOWED_PERCENTAGES.contains(input.allocationPercentage())) {
            throw new BadRequestException("allocationPercentage deve ser 25, 50, 75 ou 100");
        }
    }

    private DemandProfileRequirementResponse response(DemandProfileRequirement requirement) {
        Profile profile = requirement.getProfile();
        return new DemandProfileRequirementResponse(requirement.getId(), profile.getId(), profile.getName(),
                profile.getCategory(), profile.getAvailableCapacity(), profile.isActive(),
                requirement.getRequiredQuantity(), requirement.getAllocationPercentage());
    }

    public record RequirementSummary(int profilesCount, int totalResources) {
    }
}
