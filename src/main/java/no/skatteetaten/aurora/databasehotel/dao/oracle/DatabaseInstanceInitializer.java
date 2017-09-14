package no.skatteetaten.aurora.databasehotel.dao.oracle;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager;

@Component
public class DatabaseInstanceInitializer {

    public static final String DEFAULT_SCHEMA_NAME = "DATABASEHOTEL_INSTANCE_DATA";

    private String schemaName;

    public DatabaseInstanceInitializer() {

        this(DEFAULT_SCHEMA_NAME);
    }

    public DatabaseInstanceInitializer(String schemaName) {

        this.schemaName = schemaName;
    }

    //    @Transactional
    public void assertInitialized(DatabaseManager databaseManager, String password) {

        if (!databaseManager.schemaExists(schemaName)) {
            databaseManager.createSchema(schemaName, password);
        }
        databaseManager.updatePassword(schemaName, password);
    }

    public void migrate(DataSource dataSource) {
        Flyway flyway = new Flyway();

        flyway.setOutOfOrder(true);
        flyway.setDataSource(dataSource);
        flyway.setSchemas(schemaName);
        flyway.setTable("SCHEMA_VERSION");
        flyway.migrate();
    }
}
