package cvt.cv.ppmbackend.config;

import cvt.cv.ppmbackend.service.ProjectPhaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Migrações do plano por fases, no arranque (idempotentes; uma falha não impede o arranque):
 * <ol>
 * <li>em PostgreSQL, remove a check constraint de {@code project_event.type}: o ddl-auto=update cria-a com os
 * tipos de evento da altura e nunca a atualiza, o que recusaria tipos novos como PLAN_CHANGED;</li>
 * <li>cria as fases dos projetos que ainda não as têm;</li>
 * <li>repõe por iniciar as fases de projetos ainda não iniciados que já tinham avanço registado.</li>
 * </ol>
 */
@Component
public class ProjectPhaseBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProjectPhaseBackfill.class);

    private final ProjectPhaseService phases;
    private final DataSource dataSource;

    public ProjectPhaseBackfill(ProjectPhaseService phases, DataSource dataSource) {
        this.phases = phases;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            dropEventTypeCheck();
        } catch (Exception e) {
            log.warn("Não foi possível remover a check constraint de project_event.type: {}", e.getMessage());
        }
        try {
            phases.backfillMissingPhases();
            phases.resetPhasesOfProjectsNotStarted();
            phases.linkExistingProfiles();
        } catch (RuntimeException e) {
            log.warn("Não foi possível migrar as fases dos projetos existentes: {}", e.getMessage(), e);
        }
    }

    private void dropEventTypeCheck() throws Exception {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        }
        if (product != null && product.toLowerCase().contains("postgres")) {
            new JdbcTemplate(dataSource).execute(
                    "ALTER TABLE IF EXISTS project_event DROP CONSTRAINT IF EXISTS project_event_type_check");
        }
    }
}
