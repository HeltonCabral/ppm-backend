package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.AccessDtos.BulkResult;
import cvt.cv.ppmbackend.dto.AccessDtos.UserDirectionInput;
import cvt.cv.ppmbackend.dto.AccessDtos.UserDirectionResponse;
import cvt.cv.ppmbackend.entity.UserDirection;
import cvt.cv.ppmbackend.enums.UserDirectionSource;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.UserDirectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Atribuições utilizador → direção, geridas na página "Utilizadores por Direção". */
@Service
@Transactional
public class UserDirectionService {
    private final UserDirectionRepository userDirections;

    public UserDirectionService(UserDirectionRepository userDirections) {
        this.userDirections = userDirections;
    }

    @Transactional(readOnly = true)
    public List<UserDirectionResponse> list() {
        return userDirections.findAllByOrderByUserNameAscDirectionCodeAsc().stream().map(this::map).toList();
    }

    public UserDirectionResponse create(UserDirectionInput input, String actorId) {
        Normalized n = normalize(input, true);
        userDirections.findByUserNameAndDirectionCode(n.userName(), n.directionCode())
                .ifPresent(existing -> {
                    throw duplicate(n);
                });
        return map(userDirections.save(newAssignment(n, actorId)));
    }

    /** Pares já existentes (na base ou repetidos no lote) contam como {@code skipped}, não como erro. */
    public BulkResult bulk(List<UserDirectionInput> inputs, String actorId) {
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<UserDirectionInput> items = inputs == null ? List.of() : inputs;
        for (int i = 0; i < items.size(); i++) {
            Normalized n;
            try {
                n = normalize(items.get(i), true);
            } catch (BadRequestException e) {
                errors.add("Linha " + (i + 1) + ": " + e.getMessage());
                continue;
            }
            if (!seen.add(n.userName() + "|" + n.directionCode())
                    || userDirections.findByUserNameAndDirectionCode(n.userName(), n.directionCode()).isPresent()) {
                skipped++;
                continue;
            }
            userDirections.save(newAssignment(n, actorId));
            created++;
        }
        return new BulkResult(created, skipped, errors);
    }

    public UserDirectionResponse update(UUID id, UserDirectionInput input, String actorId) {
        UserDirection assignment = find(id);
        Normalized n = normalize(input, false);
        userDirections.findByUserNameAndDirectionCode(n.userName(), n.directionCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw duplicate(n);
                });
        // Sem nome no pedido, mantém o que havia enquanto a direção for a mesma.
        String directionName = n.directionName() == null && n.directionCode().equals(assignment.getDirectionCode())
                ? assignment.getDirectionName()
                : n.directionName();
        apply(assignment, n, directionName);
        assignment.setUpdatedBy(actor(actorId));
        return map(userDirections.save(assignment));
    }

    public void delete(UUID id) {
        userDirections.delete(find(id));
    }

    private UserDirection newAssignment(Normalized n, String actorId) {
        UserDirection assignment = new UserDirection();
        apply(assignment, n, n.directionName());
        assignment.setCreatedBy(actor(actorId));
        assignment.setUpdatedBy(actor(actorId));
        return assignment;
    }

    private void apply(UserDirection assignment, Normalized n, String directionName) {
        assignment.setUserName(n.userName());
        assignment.setFullName(n.fullName());
        assignment.setDirectionCode(n.directionCode());
        assignment.setDirectionName(directionName);
        assignment.setValidUntil(n.validUntil());
        assignment.setSource(n.source());
        assignment.setAdDepartment(n.adDepartment());
    }

    private Normalized normalize(UserDirectionInput input, boolean creation) {
        if (input == null) {
            throw new BadRequestException("Dados da atribuição em falta");
        }
        String userName = trimToNull(input.userName());
        String directionCode = trimToNull(input.directionCode());
        if (userName == null) {
            throw new BadRequestException("userName é obrigatório");
        }
        if (directionCode == null) {
            throw new BadRequestException("directionCode é obrigatório");
        }
        if (creation && input.validUntil() != null && input.validUntil().isBefore(LocalDate.now())) {
            throw new BadRequestException("validUntil não pode ser anterior a hoje");
        }
        Normalized n = new Normalized(
                userName.toLowerCase(Locale.ROOT),
                trimToNull(input.fullName()),
                directionCode.toUpperCase(Locale.ROOT),
                trimToNull(input.directionName()),
                input.validUntil(),
                input.source() == null ? UserDirectionSource.MANUAL : input.source(),
                trimToNull(input.adDepartment()));
        maxLength("userName", n.userName(), 100);
        maxLength("fullName", n.fullName(), 200);
        maxLength("directionCode", n.directionCode(), 20);
        maxLength("directionName", n.directionName(), 200);
        maxLength("adDepartment", n.adDepartment(), 300);
        return n;
    }

    private UserDirection find(UUID id) {
        return userDirections.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atribuição não encontrada: " + id));
    }

    private DomainException duplicate(Normalized n) {
        return new DomainException(HttpStatus.CONFLICT, "USER_DIRECTION_ALREADY_EXISTS",
                "O utilizador " + n.userName() + " já está atribuído à direção " + n.directionCode() + ".",
                Map.of("userName", n.userName(), "directionCode", n.directionCode()));
    }

    private UserDirectionResponse map(UserDirection u) {
        return new UserDirectionResponse(u.getId(), u.getUserName(), u.getFullName(), u.getDirectionCode(),
                u.getDirectionName(), u.getValidUntil(), u.getSource(), u.getAdDepartment(), u.getCreatedAt(),
                u.getCreatedBy(), u.getUpdatedAt());
    }

    static void maxLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new BadRequestException(field + " não pode ter mais de " + max + " caracteres");
        }
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value;
    }

    private record Normalized(String userName, String fullName, String directionCode, String directionName,
            LocalDate validUntil, UserDirectionSource source, String adDepartment) {
    }
}
