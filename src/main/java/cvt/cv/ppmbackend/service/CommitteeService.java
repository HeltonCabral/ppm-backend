package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CommitteeDtos;
import cvt.cv.ppmbackend.entity.CommitteeMember;
import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.CommitteeRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class CommitteeService {
    private final CommitteeRepository committees;
    private final DemandRepository demands;

    public CommitteeService(CommitteeRepository committees, DemandRepository demands) {
        this.committees = committees;
        this.demands = demands;
    }

    @Transactional(readOnly = true)
    public List<CommitteeDtos.Response> findAll() {
        return committees.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CommitteeDtos.Response findById(UUID id) {
        return toResponse(requireCommittee(id));
    }

    @Transactional(readOnly = true)
    public Committee findEntityById(UUID id) {
        return requireCommittee(id);
    }

    @Transactional(readOnly = true)
    public Committee findActiveEntityById(UUID id) {
        Committee committee = requireCommittee(id);
        if (committee.getStatus() != CommitteeStatus.ACTIVE) {
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "COMMITTEE_INACTIVE",
                    "O comité selecionado deve estar ativo.",
                    Map.of("committeeId", id.toString()));
        }
        return committee;
    }

    @Transactional(readOnly = true)
    public List<Committee> findByMemberCode(String username) {
        return committees.findByMemberCode(username);
    }

    public CommitteeDtos.Response create(CommitteeDtos.Request request) {
        String normalizedName = request.name().trim();
        ensureUniqueName(normalizedName, null);

        Committee committee = apply(request, new Committee(), normalizedName);
        return toResponse(save(committee));
    }

    public CommitteeDtos.Response update(UUID id, CommitteeDtos.Request request) {
        Committee committee = requireCommittee(id);
        String normalizedName = request.name().trim();
        ensureUniqueName(normalizedName, id);

        return toResponse(save(apply(request, committee, normalizedName)));
    }

    public void delete(UUID id) {
        Committee committee = requireCommittee(id);
        if (demands.existsByResponsibleCommitteeId(id)) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "COMMITTEE_IN_USE",
                    "Não é possível apagar um comité associado a demandas.",
                    Map.of("committeeId", id.toString()));
        }
        committees.delete(committee);
    }

    private Committee requireCommittee(UUID id) {
        return committees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comité não encontrado: " + id));
    }

    private void ensureUniqueName(String name, UUID currentId) {
        String nameKey = name.toLowerCase(Locale.ROOT);
        boolean exists = currentId == null
                ? committees.existsByNameKey(nameKey)
                : committees.existsByNameKeyAndIdNot(nameKey, currentId);

        if (exists) {
            throw nameConflict(name);
        }
    }

    private Committee apply(CommitteeDtos.Request request, Committee committee, String normalizedName) {
        CommitteeStatus status = parseStatus(request.status());
        List<CommitteeMember> members = normalizeUniqueMembers(request.members());
        List<String> directions = normalizeUnique(request.directions(), "directions", "direções");
        List<String> demandTypes = normalizeUnique(request.demandTypes(), "demandTypes", "tipos de demanda");
        List<String> domains = normalizeUnique(request.domains(), "domains", "domínios");
        List<String> riskLevels = normalizeUnique(request.riskLevels(), "riskLevels", "níveis de risco");

        committee.setName(normalizedName);
        committee.setNameKey(normalizedName.toLowerCase(Locale.ROOT));
        committee.setDescription(request.description() == null ? "" : request.description().trim());
        committee.setStatus(status);
        committee.setStrategicCommittee(request.isStrategicCommittee());
        committee.setMinimumBudget(request.minimumBudget());
        replace(committee.getMembers(), members);
        replace(committee.getDirections(), directions);
        replace(committee.getDemandTypes(), demandTypes);
        replace(committee.getDomains(), domains);
        replace(committee.getRiskLevels(), riskLevels);
        return committee;
    }

    private CommitteeStatus parseStatus(String status) {
        try {
            return CommitteeStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw validationError("status", "O estado deve ser ACTIVE ou INACTIVE.");
        }
    }

    private List<String> normalizeUnique(List<String> values, String field, String label) {
        List<String> normalized = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();

        for (String value : values) {
            String item = value.trim();
            if (!seen.add(item.toLowerCase(Locale.ROOT))) {
                throw validationError(field, "A lista de " + label + " não pode conter valores duplicados.");
            }
            normalized.add(item);
        }

        return normalized;
    }

    private List<CommitteeMember> normalizeUniqueMembers(List<CommitteeDtos.Member> values) {
        List<CommitteeMember> normalized = new ArrayList<>(values.size());
        Set<String> seenCodes = new HashSet<>();

        for (CommitteeDtos.Member value : values) {
            String name = value.name().trim();
            String code = value.code().trim();
            if (!seenCodes.add(code.toLowerCase(Locale.ROOT))) {
                throw validationError("members", "A lista de membros não pode conter códigos duplicados.");
            }
            normalized.add(new CommitteeMember(name, code));
        }

        return normalized;
    }

    private <T> void replace(List<T> target, List<T> values) {
        target.clear();
        target.addAll(values);
    }

    private Committee save(Committee committee) {
        try {
            return committees.saveAndFlush(committee);
        } catch (DataIntegrityViolationException exception) {
            throw nameConflict(committee.getName());
        }
    }

    private DomainException nameConflict(String name) {
        return new DomainException(
                HttpStatus.CONFLICT,
                "COMMITTEE_NAME_CONFLICT",
                "Já existe um comité com o nome '" + name + "'.",
                Map.of("field", "name"));
    }

    private DomainException validationError(String field, String message) {
        return new DomainException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                message,
                Map.of("field", field));
    }

    private CommitteeDtos.Response toResponse(Committee committee) {
        return new CommitteeDtos.Response(
                committee.getId(),
                committee.getName(),
                committee.getDescription(),
                committee.getStatus(),
                committee.isStrategicCommittee(),
                committee.getMembers().stream()
                        .map(member -> new CommitteeDtos.Member(member.getName(), member.getCode()))
                        .toList(),
                List.copyOf(committee.getDirections()),
                List.copyOf(committee.getDemandTypes()),
                List.copyOf(committee.getDomains()),
                List.copyOf(committee.getRiskLevels()),
                committee.getMinimumBudget(),
                committee.getCreatedAt(),
                committee.getUpdatedAt());
    }
}
