package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.AccessDtos.DepartmentMappingInput;
import cvt.cv.ppmbackend.dto.AccessDtos.DepartmentMappingResponse;
import cvt.cv.ppmbackend.entity.DepartmentDirectionMapping;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DepartmentDirectionMappingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static cvt.cv.ppmbackend.service.UserDirectionService.maxLength;
import static cvt.cv.ppmbackend.service.UserDirectionService.trimToNull;

/** Traduções departamento do AD → direção do hub, usadas pelo frontend para sugerir a direção. */
@Service
@Transactional
public class DepartmentMappingService {
    private final DepartmentDirectionMappingRepository mappings;

    public DepartmentMappingService(DepartmentDirectionMappingRepository mappings) {
        this.mappings = mappings;
    }

    @Transactional(readOnly = true)
    public List<DepartmentMappingResponse> list() {
        return mappings.findAllByOrderByDepartmentKeyAsc().stream().map(this::map).toList();
    }

    public DepartmentMappingResponse create(DepartmentMappingInput input, String actorId) {
        DepartmentDirectionMapping mapping = new DepartmentDirectionMapping();
        apply(mapping, input, null);
        mapping.setCreatedBy(actor(actorId));
        mapping.setUpdatedBy(actor(actorId));
        return map(mappings.save(mapping));
    }

    public DepartmentMappingResponse update(UUID id, DepartmentMappingInput input, String actorId) {
        DepartmentDirectionMapping mapping = find(id);
        apply(mapping, input, id);
        mapping.setUpdatedBy(actor(actorId));
        return map(mappings.save(mapping));
    }

    public void delete(UUID id) {
        mappings.delete(find(id));
    }

    private void apply(DepartmentDirectionMapping mapping, DepartmentMappingInput input, UUID currentId) {
        if (input == null) {
            throw new BadRequestException("Dados da tradução em falta");
        }
        String key = normalizeKey(input.departmentKey());
        String directionCode = trimToNull(input.directionCode());
        if (key == null) {
            throw new BadRequestException("departmentKey é obrigatório");
        }
        if (directionCode == null) {
            throw new BadRequestException("directionCode é obrigatório");
        }
        directionCode = directionCode.toUpperCase(Locale.ROOT);
        String directionName = trimToNull(input.directionName());
        maxLength("departmentKey", key, 100);
        maxLength("directionCode", directionCode, 20);
        maxLength("directionName", directionName, 200);
        mappings.findByDepartmentKey(key)
                .filter(other -> !other.getId().equals(currentId))
                .ifPresent(other -> {
                    throw new DomainException(HttpStatus.CONFLICT, "DEPARTMENT_MAPPING_ALREADY_EXISTS",
                            "Já existe uma tradução para o departamento " + key + ".",
                            Map.of("departmentKey", key));
                });
        // Sem nome no pedido, mantém o que havia enquanto a direção for a mesma.
        if (directionName == null && directionCode.equals(mapping.getDirectionCode())) {
            directionName = mapping.getDirectionName();
        }
        mapping.setDepartmentKey(key);
        mapping.setDirectionCode(directionCode);
        mapping.setDirectionName(directionName);
    }

    /** Mesma normalização do frontend: maiúsculas, sem acentos. */
    static String normalizeKey(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    private DepartmentDirectionMapping find(UUID id) {
        return mappings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tradução não encontrada: " + id));
    }

    private DepartmentMappingResponse map(DepartmentDirectionMapping m) {
        return new DepartmentMappingResponse(m.getId(), m.getDepartmentKey(), m.getDirectionCode(),
                m.getDirectionName(), m.getCreatedAt(), m.getCreatedBy());
    }

    private static String actor(String value) {
        return value == null || value.isBlank() ? "system" : value;
    }
}
