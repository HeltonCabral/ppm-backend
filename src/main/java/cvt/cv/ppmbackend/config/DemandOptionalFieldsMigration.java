package cvt.cv.ppmbackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Em PostgreSQL, deixa {@code demands.problem_statement} e {@code demands.desired_outcome} aceitar nulos:
 * deixaram de ser obrigatórios na criação (uma demanda importada do Excel não os traz), e o
 * ddl-auto=update nunca remove um NOT NULL que já criou. Idempotente; uma falha não impede o arranque.
 * O mesmo, para aplicar à mão: {@code db/2026-10-16-demand-optional-context.sql}.
 */
@Component
public class DemandOptionalFieldsMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemandOptionalFieldsMigration.class);

    private final DataSource dataSource;

    public DemandOptionalFieldsMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String product;
            try (Connection connection = dataSource.getConnection()) {
                product = connection.getMetaData().getDatabaseProductName();
            }
            if (product == null || !product.toLowerCase().contains("postgres")) {
                return;
            }
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("ALTER TABLE IF EXISTS demands ALTER COLUMN problem_statement DROP NOT NULL");
            jdbc.execute("ALTER TABLE IF EXISTS demands ALTER COLUMN desired_outcome DROP NOT NULL");
        } catch (Exception e) {
            log.warn("Não foi possível tornar opcionais problem_statement e desired_outcome em demands: {}", e.getMessage());
        }
    }
}
