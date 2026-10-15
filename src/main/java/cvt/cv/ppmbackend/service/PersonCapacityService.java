package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PersonCapacityDtos.Request;
import cvt.cv.ppmbackend.dto.PersonCapacityDtos.Response;
import cvt.cv.ppmbackend.entity.PersonCapacity;
import cvt.cv.ppmbackend.enums.CapacityReason;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.PersonCapacityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Capacidade individual das pessoas. Com o filtro de projetos ligado, a escrita exige
 * {@code projeto_editar_todos}.
 */
@Service
@Transactional
public class PersonCapacityService {
    private final PersonCapacityRepository capacities;
    private final ProjectGuard guard;

    public PersonCapacityService(PersonCapacityRepository capacities, ProjectGuard guard) {
        this.capacities = capacities;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<Response> list(String userName) {
        List<PersonCapacity> rows = userName == null || userName.isBlank()
                ? capacities.findAllByOrderByUserNameAscValidFromAsc()
                : capacities.findByUserNameOrderByValidFromAsc(normalize(userName));
        return rows.stream().map(PersonCapacityService::toResponse).toList();
    }

    public Response create(Request request, String actor) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode alterar a capacidade das pessoas.");
        PersonCapacity capacity = new PersonCapacity();
        capacity.setCreatedBy(actor(actor));
        apply(capacity, request, actor);
        return toResponse(capacities.save(capacity));
    }

    public Response update(UUID id, Request request, String actor) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode alterar a capacidade das pessoas.");
        PersonCapacity capacity = require(id);
        apply(capacity, request, actor);
        return toResponse(capacities.save(capacity));
    }

    public void delete(UUID id) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode alterar a capacidade das pessoas.");
        capacities.delete(require(id));
    }

    /** Registos de cada pessoa, por {@code userName} em minúsculas. */
    @Transactional(readOnly = true)
    public Map<String, List<PersonCapacity>> byPerson(Collection<String> userNames) {
        List<String> keys = userNames.stream().filter(u -> u != null && !u.isBlank()).map(PersonCapacityService::normalize)
                .distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        return capacities.findByUserNameIn(keys).stream()
                .collect(Collectors.groupingBy(PersonCapacity::getUserName));
    }

    private void apply(PersonCapacity capacity, Request r, String actor) {
        if (r == null || r.userName() == null || r.userName().isBlank()) {
            throw new BadRequestException("userName é obrigatório.");
        }
        String userName = normalize(r.userName());
        if (userName.length() > 100) {
            throw new BadRequestException("userName não pode exceder 100 caracteres.");
        }
        String fullName = trim(r.fullName());
        if (fullName != null && fullName.length() > 200) {
            throw new BadRequestException("fullName não pode exceder 200 caracteres.");
        }
        if (r.capacityPercentage() == null || r.capacityPercentage() < 0 || r.capacityPercentage() > 100
                || (r.capacityPercentage() == 100 && r.maxConcurrent() == null)) {
            throw new BadRequestException("A capacidade tem de estar entre 0 e 99%; 100% exige maxConcurrent.");
        }
        if (r.maxConcurrent() != null && (r.maxConcurrent() < 1 || r.maxConcurrent() > 20)) {
            throw new BadRequestException("maxConcurrent tem de estar entre 1 e 20.");
        }
        if (r.reason() == null) {
            throw new BadRequestException("Indique o motivo.");
        }
        if (r.validFrom() != null && r.validUntil() != null && r.validUntil().isBefore(r.validFrom())) {
            throw new BadRequestException("validUntil não pode ser anterior a validFrom.");
        }
        if (r.reason() == CapacityReason.LEAVE && (r.validFrom() == null || r.validUntil() == null)) {
            throw new BadRequestException("Uma ausência precisa de data de início e de fim.");
        }
        boolean permanent = r.validFrom() == null && r.validUntil() == null;
        if (permanent) {
            boolean another = capacities.findByUserNameOrderByValidFromAsc(userName).stream()
                    .anyMatch(other -> other.isPermanent() && !other.getId().equals(capacity.getId()));
            if (another) {
                throw new DomainException(HttpStatus.CONFLICT, "PERSON_CAPACITY_PERMANENT_EXISTS",
                        (fullName != null ? fullName : userName) + " já tem uma capacidade permanente.",
                        Map.of("userName", userName));
            }
        }
        capacity.setUserName(userName);
        capacity.setFullName(fullName);
        capacity.setCapacityPercentage(r.capacityPercentage());
        capacity.setMaxConcurrent(r.maxConcurrent());
        capacity.setReason(r.reason());
        capacity.setValidFrom(r.validFrom());
        capacity.setValidUntil(r.validUntil());
        capacity.setNotes(trim(r.notes()));
        capacity.setUpdatedBy(actor(actor));
    }

    private PersonCapacity require(UUID id) {
        return capacities.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Capacidade não encontrada: " + id));
    }

    static Response toResponse(PersonCapacity c) {
        return new Response(c.getId(), c.getUserName(), c.getFullName(), c.getCapacityPercentage(), c.getMaxConcurrent(), c.getReason(),
                c.getValidFrom(), c.getValidUntil(), c.getNotes(), c.getCreatedAt(), c.getCreatedBy(),
                c.getUpdatedAt(), c.getUpdatedBy());
    }

    static String normalize(String userName) {
        return userName.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
