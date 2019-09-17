package no.skatteetaten.aurora.databasehotel.domain

import java.util.Date
import java.util.HashMap
import no.skatteetaten.aurora.databasehotel.DatabaseEngine

object DomainUtils {

    fun createDatabaseSchema(id: String = "ID"): DatabaseSchema {
        return DatabaseSchema(
            id,
            metaInfo("test", "localhost", 1521, true),
            "jdbc", "local",
            Date(), Date(),
            DatabaseSchemaMetaData(0.0)
        )
    }

    fun metaInfo(
        name: String,
        host: String,
        port: Int?,
        createSchemaAllowed: Boolean? = true,
        engine: DatabaseEngine = DatabaseEngine.ORACLE,
        labels: Map<String, String> = HashMap()
    ): DatabaseInstanceMetaInfo {
        return DatabaseInstanceMetaInfo(engine, name, host, port!!, createSchemaAllowed!!, labels)
    }
}
