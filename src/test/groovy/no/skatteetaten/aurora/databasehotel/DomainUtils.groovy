package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData

class DomainUtils {

  static DatabaseSchema createDatabaseSchema() {
    new DatabaseSchema("ID", new DatabaseInstanceMetaInfo("test", "localhost", 1521), "jdbc", "local",
        new Date(), new Date(), new DatabaseSchemaMetaData(0.0))
  }
}
