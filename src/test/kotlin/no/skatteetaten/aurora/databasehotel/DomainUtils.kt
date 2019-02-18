package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import java.util.Date

class DomainUtils {

    companion object {
        @JvmStatic
        @JvmOverloads
        fun createDatabaseSchema(id: String = "ID") = DatabaseSchema(
            id,
            createDatabaseInstanceMetaInfo("test", "localhost", 1521, true),
            "jdbc", "local",
            Date(), Date(),
            DatabaseSchemaMetaData(0.0)
        )

        @JvmStatic
        @JvmOverloads
        fun createDatabaseInstanceMetaInfo(
            name: String,
            host: String,
            port: Int,
            createSchemaAllowed: Boolean = true,
            engine: DatabaseEngine = DatabaseEngine.ORACLE
        ) = DatabaseInstanceMetaInfo(engine, name, host, port, createSchemaAllowed, null)
    }
}
