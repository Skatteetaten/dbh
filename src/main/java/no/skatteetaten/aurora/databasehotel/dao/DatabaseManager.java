package no.skatteetaten.aurora.databasehotel.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import no.skatteetaten.aurora.databasehotel.dao.dto.Schema;

public interface DatabaseManager {
    boolean schemaExists(String schemaName);

    String createSchema(String schemaName, String password);

    void updatePassword(String schemaName, String password);

    Optional<Schema> findSchemaByName(String name);

    List<Schema> findAllNonSystemSchemas();

    void deleteSchema(String schemaName);

    void executeStatements(String... statements);

    String getCurrentUserName();

    List<Map<String, Object>> query(String query, Object... params);

    DataSource getDataSource();
}
