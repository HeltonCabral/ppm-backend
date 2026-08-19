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
       // ensureUniqueName(name, null);
        Profile profile = new Profile();
        apply(profile, name,request.directionCode(),request.directionName(), request.description(), request.availableCapacity(),request.simultaneousDemandCapacity());
        profile.setActive(true);
        return Response.from(profiles.save(profile));
    }

    public Response update(UUID id, UpdateRequest request) {
        Profile profile = entity(id);
        String name = normalizedName(request.name());
       // ensureUniqueName(name, id);
        apply(profile, name,request.directionCode(),request.directionName() , request.description(), request.availableCapacity(), request.simultaneousDemandCapacity());
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

    private void apply(Profile profile, String name, String directionCode, String directionName,
            String description, Integer availableCapacity, Integer simultaneousDemandCapacity) {
        
        profile.setName(name);
        profile.setDirectionCode(directionCode == null ? null : directionCode.trim());
        profile.setDirectionName(directionName == null ? null : directionName.trim());
        profile.setDescription(description == null ? null : description.trim());
        profile.setSimultaneousDemandCapacity(simultaneousDemandCapacity);
        profile.setAvailableCapacity(availableCapacity);
    }

    private String normalizedName(String name) {
        if (name == null || name.isBlank()) throw new BadRequestException("name é obrigatório");
        return name.trim();
    }

}
