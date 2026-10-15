package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CapacitySettingsDto;
import cvt.cv.ppmbackend.entity.CapacitySettings;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.CapacitySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CapacitySettingsService {
    public static final int DEFAULT_MAX_CONCURRENT = 3;

    private final CapacitySettingsRepository settings;
    private final ProjectGuard guard;

    public CapacitySettingsService(CapacitySettingsRepository settings, ProjectGuard guard) {
        this.settings = settings;
        this.guard = guard;
    }

    public CapacitySettingsDto get() { return new CapacitySettingsDto(value()); }

    public int value() {
        return settings.findFirstByOrderByIdAsc().map(CapacitySettings::getMaxConcurrentProjects)
                .orElse(DEFAULT_MAX_CONCURRENT);
    }

    public CapacitySettingsDto update(CapacitySettingsDto request) {
        guard.requireEditAll("Só quem tem projeto_editar_todos pode alterar as definições de capacidade.");
        if (request == null || request.maxConcurrentProjects() == null
                || request.maxConcurrentProjects() < 1 || request.maxConcurrentProjects() > 20) {
            throw new BadRequestException("maxConcurrentProjects tem de estar entre 1 e 20.");
        }
        CapacitySettings entity = settings.findFirstByOrderByIdAsc().orElseGet(CapacitySettings::new);
        entity.setMaxConcurrentProjects(request.maxConcurrentProjects());
        settings.save(entity);
        return new CapacitySettingsDto(entity.getMaxConcurrentProjects());
    }
}
