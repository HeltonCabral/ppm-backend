package cvt.cv.ppmbackend.config;

import cvt.cv.ppmbackend.entity.StrategicPlan;
import cvt.cv.ppmbackend.enums.StrategicPlanStatus;
import cvt.cv.ppmbackend.repository.StrategicPlanRepository;
import cvt.cv.ppmbackend.service.StrategicPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Optional;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.rollover.enabled", havingValue = "true")
public class RolloverJob {
    private static final Logger log = LoggerFactory.getLogger(RolloverJob.class);
    private final StrategicPlanRepository repository;
    private final StrategicPlanService service;

    public RolloverJob(StrategicPlanRepository repository, StrategicPlanService service) {
        this.repository = repository;
        this.service = service;
    }

    // Rodar todo dia às 03:00; a lógica dispara apenas no último dia útil do ano fiscal.
    @Scheduled(cron = "0 0 3 * * *")
    public void maybeRollover() {
        LocalDate today = LocalDate.now();
        if (today.getMonthValue() != 12 || today.getDayOfMonth() < 28)
            return;
        Optional<StrategicPlan> active = repository.findFirstByStatus(StrategicPlanStatus.ACTIVE);
        active.ifPresent(current -> {
            if (current.getEndYear() != null && current.getEndYear() == today.getYear()) {
                log.info("Rollover automático do plano estratégico {}", current.getName());
                service.rollover(current.getId());
            }
        });
    }
}
