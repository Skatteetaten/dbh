package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData

class DomainUtils {

  static DatabaseSchema createDatabaseSchema() {
    new DatabaseSchema("ID", createDatabaseInstanceMetaInfo("test", "localhost", 1521, true), "jdbc", "local",
        new Date(), new Date(), new DatabaseSchemaMetaData(0.0))
  }

  static DatabaseInstanceMetaInfo createDatabaseInstanceMetaInfo(String name, String host, Integer port,
      Boolean createSchemaAllowed = true, DatabaseEngine engine = DatabaseEngine.ORACLE) {
    new DatabaseInstanceMetaInfo(engine, name, host, port, createSchemaAllowed)
  }
}
