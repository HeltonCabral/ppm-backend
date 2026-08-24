package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.StrategicPlanningConfigDtos.UpdateRequest;
import cvt.cv.ppmbackend.entity.StrategicPlanningConfig;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.StrategicPlanningConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StrategicPlanningConfigService {
    private final StrategicPlanningConfigRepository configs;

    public StrategicPlanningConfigService(StrategicPlanningConfigRepository configs) {
        this.configs = configs;
    }

    public StrategicPlanningConfig getActive() {
        return configs.findFirstByActiveTrueOrderByCreatedAtDesc().orElseGet(this::createDefault);
    }

    public StrategicPlanningConfig update(UpdateRequest request) {
        if (request.horizonYears() < 2) {
            throw new BadRequestException("horizonYears deve ser maior ou igual a 2");
        }
        StrategicPlanningConfig config = getActive();
        config.setRollingPlan(request.rollingPlan());
        config.setHorizonYears(request.horizonYears());
        config.setReviewFrequency(request.reviewFrequency());
        return configs.save(config);
    }

    private StrategicPlanningConfig createDefault() {
        StrategicPlanningConfig config = new StrategicPlanningConfig();
        return configs.save(config);
    }
}
