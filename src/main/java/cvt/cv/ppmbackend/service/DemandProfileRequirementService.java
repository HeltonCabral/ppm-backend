package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.DemandPhaseRef;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementInput;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandProfileRequirementResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandProfileRequirementRepository;
import cvt.cv.ppmbackend.repository.PhaseDefinitionRepository;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DemandProfileRequirementService {

    private final DemandProfileRequirementRepository requirements;
    private final ProfileRepository profiles;
    private final PhaseDefinitionRepository phases;

    public DemandProfileRequirementService(DemandProfileRequirementRepository requirements,
            ProfileRepository profiles, PhaseDefinitionRepository phases) {
        this.requirements = requirements;
        this.profiles = profiles;
        this.phases = phases;
    }

    public void sync(Demand demand, List<DemandProfileRequirementInput> inputs) {
        if (inputs == null) {
            return;
        }
        Map<UUID, PhaseDefinition> allowed = allowedPhases(demand);
        List<DemandProfileRequirement> current = requirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId());
        Map<UUID, DemandProfileRequirement> currentById = new HashMap<>();
        current.forEach(r -> currentById.put(r.getId(), r));

        Set<UUID> keptIds = new HashSet<>();
        List<DemandProfileRequirement> toSave = new ArrayList<>();
        // profileId -> fases já usadas por outra entrada do mesmo perfil neste payload
        Map<UUID, Set<UUID>> phasesByProfile = new HashMap<>();

        for (DemandProfileRequirementInput input : inputs) {
            validateInput(input);
            Profile profile = loadProfile(input.profileId());
            DemandProfileRequirement entity;
            if (input.id() != null) {
                entity = currentById.get(input.id());
                if (entity == null) {
                    throw new ResourceNotFoundException("Entrada de perfil não encontrada na Demanda: " + input.id());
                }
                if (!entity.getProfile().getId().equals(input.profileId())) {
                    throw new BadRequestException(
                            "Não é permitido alterar o perfil de uma entrada existente: " + input.id());
                }
                keptIds.add(entity.getId());
            } else {
                if (!profile.isActive()) {
                    throw new BadRequestException(
                            "Perfis inativos não podem ser associados a novas Demandas: " + profile.getName());
                }
                entity = new DemandProfileRequirement();
                entity.setDemand(demand);
                entity.setProfile(profile);
            }
            Set<PhaseDefinition> resolvedPhases = resolvePhases(input.phaseIds(), allowed, demand, profile);
            registerPhases(phasesByProfile, profile, input.phaseIds());
            entity.setRequiredQuantity(input.requiredQuantity());
            entity.setPhases(resolvedPhases);
            toSave.add(entity);
        }

        List<DemandProfileRequirement> toDelete = current.stream()
                .filter(r -> !keptIds.contains(r.getId()))
                .toList();
        requirements.deleteAll(toDelete);
        requirements.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public List<DemandProfileRequirementResponse> list(UUID demandId) {
        return requirements.findByDemand_IdOrderByProfile_NameAsc(demandId).stream()
                .map(this::toResponse)
                .toList();
    }

    public DemandProfileRequirementResponse addRequirement(Demand demand, DemandProfileRequirementInput input) {
        validateInput(input);
        Profile profile = loadProfile(input.profileId());
        if (!profile.isActive()) {
            throw new BadRequestException(
                    "Perfis inativos não podem ser associados a novas Demandas: " + profile.getName());
        }
        Map<UUID, PhaseDefinition> allowed = allowedPhases(demand);
        assertNoPhaseOverlap(demand.getId(), input.profileId(), input.phaseIds(), null, profile);
        DemandProfileRequirement entity = new DemandProfileRequirement();
        entity.setDemand(demand);
        entity.setProfile(profile);
        entity.setRequiredQuantity(input.requiredQuantity());
        entity.setPhases(resolvePhases(input.phaseIds(), allowed, demand, profile));
        return toResponse(requirements.save(entity));
    }

    public DemandProfileRequirementResponse updateRequirement(Demand demand, UUID requirementId,
            DemandProfileRequirementInput input) {
        validateInput(input);
        DemandProfileRequirement entity = requireEntity(demand.getId(), requirementId);
        if (!entity.getProfile().getId().equals(input.profileId())) {
            throw new BadRequestException("Não é permitido alterar o perfil de uma entrada existente.");
        }
        Map<UUID, PhaseDefinition> allowed = allowedPhases(demand);
        assertNoPhaseOverlap(demand.getId(), input.profileId(), input.phaseIds(), requirementId, entity.getProfile());
        entity.setRequiredQuantity(input.requiredQuantity());
        entity.setPhases(resolvePhases(input.phaseIds(), allowed, demand, entity.getProfile()));
        return toResponse(requirements.save(entity));
    }

    public void removeRequirement(Demand demand, UUID requirementId) {
        requirements.delete(requireEntity(demand.getId(), requirementId));
    }

    public void handleTypeChange(Demand demand) {
        List<DemandProfileRequirement> current = requirements.findByDemand_IdOrderByProfile_NameAsc(demand.getId());
        if (current.isEmpty()) {
            return;
        }
        if (demand.getType() == null) {
            requirements.deleteAll(current);
            return;
        }
        Set<UUID> allowed = allowedPhases(demand).keySet();
        List<DemandProfileRequirement> toDrop = new ArrayList<>();
        List<DemandProfileRequirement> toSave = new ArrayList<>();
        for (DemandProfileRequirement entity : current) {
            Set<PhaseDefinition> kept = new LinkedHashSet<>();
            for (PhaseDefinition phase : entity.getPhases()) {
                if (allowed.contains(phase.getId())) {
                    kept.add(phase);
                }
            }
            if (kept.isEmpty()) {
                toDrop.add(entity);
            } else if (kept.size() != entity.getPhases().size()) {
                entity.setPhases(kept);
                toSave.add(entity);
            }
        }
        requirements.deleteAll(toDrop);
        requirements.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public RequirementSummary summary(UUID demandId) {
        List<DemandProfileRequirement> rows = requirements.findByDemand_IdOrderByProfile_NameAsc(demandId);
        int totalResources = rows.stream().mapToInt(DemandProfileRequirement::getRequiredQuantity).sum();
        int profilesCount = (int) rows.stream().map(r -> r.getProfile().getId()).distinct().count();
        return new RequirementSummary(profilesCount, totalResources);
    }

    private DemandProfileRequirement requireEntity(UUID demandId, UUID requirementId) {
        DemandProfileRequirement entity = requirements.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada de perfil não encontrada: " + requirementId));
        if (entity.getDemand() == null || !entity.getDemand().getId().equals(demandId)) {
            throw new ResourceNotFoundException("Entrada de perfil não pertence à Demanda: " + requirementId);
        }
        return entity;
    }

    private DemandProfileRequirementResponse toResponse(DemandProfileRequirement entity) {
        Profile profile = entity.getProfile();
        List<DemandPhaseRef> phases = entity.getPhases().stream()
                .sorted(Comparator.comparing(PhaseDefinition::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new DemandPhaseRef(p.getId(), p.getCode(), p.getName(), p.getSequence()))
                .toList();
        return new DemandProfileRequirementResponse(entity.getId(), profile.getId(), profile.getName(),
                profile.getDirectionCode(), profile.getAvailableCapacity(), profile.isActive(),
                entity.getRequiredQuantity(), phases);
    }

    private void registerPhases(Map<UUID, Set<UUID>> phasesByProfile, Profile profile, List<UUID> phaseIds) {
        Set<UUID> used = phasesByProfile.computeIfAbsent(profile.getId(), k -> new HashSet<>());
        for (UUID phaseId : phaseIds) {
            if (!used.add(phaseId)) {
                throw phaseOverlap(profile);
            }
        }
    }

    private void assertNoPhaseOverlap(UUID demandId, UUID profileId, List<UUID> phaseIds, UUID excludeRequirementId,
            Profile profile) {
        Set<UUID> taken = new HashSet<>();
        requirements.findByDemand_IdOrderByProfile_NameAsc(demandId).stream()
                .filter(r -> r.getProfile().getId().equals(profileId))
                .filter(r -> excludeRequirementId == null || !r.getId().equals(excludeRequirementId))
                .forEach(r -> r.getPhases().forEach(p -> taken.add(p.getId())));
        for (UUID phaseId : phaseIds) {
            if (taken.contains(phaseId)) {
                throw phaseOverlap(profile);
            }
        }
    }

    private DomainException phaseOverlap(Profile profile) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "PROFILE_PHASE_DUPLICATED",
                "O perfil " + profile.getName() + " não pode repetir a mesma fase em entradas diferentes.",
                Map.of("profileId", profile.getId().toString()));
    }

    private Set<PhaseDefinition> resolvePhases(List<UUID> phaseIds, Map<UUID, PhaseDefinition> allowed,
            Demand demand, Profile profile) {
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID id : phaseIds) {
            if (!unique.add(id)) {
                throw new BadRequestException(
                        "A lista de fases do perfil " + profile.getName() + " contém duplicados.");
            }
        }
        Set<PhaseDefinition> resolved = new LinkedHashSet<>();
        for (UUID id : unique) {
            PhaseDefinition phase = allowed.get(id);
            if (phase == null) {
                PhaseDefinition stored = phases.findById(id).orElse(null);
                throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "PHASE_NOT_IN_DEMAND_TYPE",
                        "A fase selecionada não pertence ao modelo de fases ativo da Demanda.",
                        Map.of("phaseId", id.toString(),
                                "phaseModelId", demand.getType().getId().toString(),
                                "found", String.valueOf(stored != null)));
            }
            resolved.add(phase);
        }
        return resolved;
    }

    private Map<UUID, PhaseDefinition> allowedPhases(Demand demand) {
        PhaseModel model = demand.getType();
        if (model == null) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "DEMAND_TYPE_REQUIRED",
                    "A Demanda não tem modelo de fases (type) definido; selecione o modelo antes de associar perfis.",
                    Map.of("demandId", demand.getId() == null ? "null" : demand.getId().toString()));
        }
        Map<UUID, PhaseDefinition> out = new LinkedHashMap<>();
        for (PhaseDefinition phase : model.getPhases()) {
            if (phase.isActive()) {
                out.put(phase.getId(), phase);
            }
        }
        return out;
    }

    private Profile loadProfile(UUID profileId) {
        return profiles.findById(profileId)
                .orElseThrow(() -> new BadRequestException("Perfil não encontrado: " + profileId));
    }

    private void validateInput(DemandProfileRequirementInput input) {
        if (input == null || input.profileId() == null) {
            throw new BadRequestException("profileId é obrigatório");
        }
        if (input.requiredQuantity() == null || input.requiredQuantity() < 1) {
            throw new BadRequestException("requiredQuantity deve ser maior ou igual a 1");
        }
        if (input.phaseIds() == null || input.phaseIds().isEmpty()) {
            throw new BadRequestException("A lista de fases é obrigatória para o perfil " + input.profileId());
        }
    }

    public record RequirementSummary(int profilesCount, int totalResources) {
    }
}
