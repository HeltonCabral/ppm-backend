package cvt.cv.ppmbackend.config;

import cvt.cv.ppmbackend.service.ProjectPhaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Migração do plano por fases: no arranque, cria as fases dos projetos que ainda não as têm (a partir do
 * planeamento da Demanda de origem ou do Tipo do projeto). Idempotente; projetos sem datas previstas ficam
 * com as datas das fases a null.
 */
@Component
public class ProjectPhaseBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProjectPhaseBackfill.class);

    private final ProjectPhaseService phases;

    public ProjectPhaseBackfill(ProjectPhaseService phases) {
        this.phases = phases;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            phases.backfillMissingPhases();
        } catch (RuntimeException e) {
            // Não impede o arranque: a migração volta a correr no próximo.
            log.warn("Não foi possível criar as fases dos projetos existentes: {}", e.getMessage(), e);
        }
    }
}
