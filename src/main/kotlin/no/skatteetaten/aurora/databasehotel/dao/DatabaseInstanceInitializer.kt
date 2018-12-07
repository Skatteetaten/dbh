package no.skatteetaten.aurora.databasehotel.dao

import javax.sql.DataSource

import org.flywaydb.core.Flyway
import org.springframework.stereotype.Component

@Component
class DatabaseInstanceInitializer @JvmOverloads constructor(private val schemaName: String = DEFAULT_SCHEMA_NAME) {

    fun assertInitialized(databaseManager: DatabaseManager, password: String) {

        if (!databaseManager.schemaExists(schemaName)) {
            databaseManager.createSchema(schemaName, password)
        }
        databaseManager.updatePassword(schemaName, password)
    }

    fun migrate(dataSource: DataSource) {
        val flyway = Flyway().apply {
            isOutOfOrder = true
            this.dataSource = dataSource
            this.setSchemas(schemaName)
            table = "SCHEMA_VERSION"
            setLocations("db/migration-postgres")
        }

        flyway.migrate()
    }

    companion object {

        @JvmField
        val DEFAULT_SCHEMA_NAME: String = "DATABASEHOTEL_INSTANCE_DATA".toLowerCase()
    }
}
