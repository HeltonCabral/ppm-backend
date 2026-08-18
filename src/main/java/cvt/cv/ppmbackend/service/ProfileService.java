package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProfileDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.ProfileDtos.Response;
import cvt.cv.ppmbackend.dto.ProfileDtos.UpdateRequest;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProfileService {
    private final ProfileRepository profiles;

    public ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return profiles.findAllByOrderByNameAsc().stream().map(Response::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listActive() {
        return profiles.findByActiveTrueOrderByNameAsc().stream().map(Response::from).toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return Response.from(entity(id));
    }

    public Response create(CreateRequest request) {
        String name = normalizedName(request.name());
        ensureUniqueName(name, null);
        Profile profile = new Profile();
        apply(profile, name, request.category(), request.description(), request.availableCapacity());
        profile.setActive(true);
        return Response.from(profiles.save(profile));
    }

    public Response update(UUID id, UpdateRequest request) {
        Profile profile = entity(id);
        String name = normalizedName(request.name());
        ensureUniqueName(name, id);
        apply(profile, name, request.category(), request.description(), request.availableCapacity());
        return Response.from(profiles.save(profile));
    }

    public Response activate(UUID id) {
        Profile profile = entity(id);
        profile.setActive(true);
        return Response.from(profiles.save(profile));
    }

    public Response deactivate(UUID id) {
        Profile profile = entity(id);
        profile.setActive(false);
        return Response.from(profiles.save(profile));
    }

    @Transactional(readOnly = true)
    public Profile entity(UUID id) {
        return profiles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil não encontrado: " + id));
    }

    private void apply(Profile profile, String name, cvt.cv.ppmbackend.enums.ProfileCategory category,
            String description, Integer availableCapacity) {
        if (category == null) throw new BadRequestException("category é obrigatória");
        if (availableCapacity == null || availableCapacity < 0) {
            throw new BadRequestException("availableCapacity deve ser um inteiro maior ou igual a zero");
        }
        profile.setName(name);
        profile.setCategory(category);
        profile.setDescription(description == null ? null : description.trim());
        profile.setAvailableCapacity(availableCapacity);
    }

    private String normalizedName(String name) {
        if (name == null || name.isBlank()) throw new BadRequestException("name é obrigatório");
        return name.trim();
    }

    private void ensureUniqueName(String name, UUID currentId) {
        profiles.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) {
                throw new BadRequestException("Já existe um perfil com o nome: " + name);
            }
        });
    }
}
