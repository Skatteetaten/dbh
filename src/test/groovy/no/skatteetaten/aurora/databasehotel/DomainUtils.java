package no.skatteetaten.aurora.databasehotel;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData;

public class DomainUtils {

    public static DatabaseSchema createDatabaseSchema() {
        return createDatabaseSchema("ID");
    }

    public static DatabaseSchema createDatabaseSchema(String id) {
        return new DatabaseSchema(
            id,
            createDatabaseInstanceMetaInfo("test", "localhost", 1521, true),
            "jdbc", "local",
            new Date(), new Date(),
            new DatabaseSchemaMetaData(0.0)
        );
    }

    public static DatabaseInstanceMetaInfo createDatabaseInstanceMetaInfo(String name, String host, Integer port,
        Boolean createSchemaAllowed) {
        return createDatabaseInstanceMetaInfo(name, host, port, createSchemaAllowed, DatabaseEngine.ORACLE,
            new HashMap<>());
    }

    public static DatabaseInstanceMetaInfo createDatabaseInstanceMetaInfo(String name, String host, Integer port) {
        return createDatabaseInstanceMetaInfo(name, host, port, true, DatabaseEngine.ORACLE, new HashMap<>());
    }

    public static DatabaseInstanceMetaInfo createDatabaseInstanceMetaInfo(String name,
        String host,
        Integer port,
        Boolean createSchemaAllowed,
        DatabaseEngine engine,
        Map<String, String> labels) {
        return new DatabaseInstanceMetaInfo(engine, name, host, port, createSchemaAllowed, labels);
    }
}
