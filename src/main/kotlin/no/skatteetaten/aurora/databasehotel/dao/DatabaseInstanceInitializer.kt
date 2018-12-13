package no.skatteetaten.aurora.databasehotel.dao

import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.databaseEngine
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

    fun migrate(dataSource: HikariDataSource) {

        val engine = dataSource.jdbcUrl.databaseEngine

        val flyway = Flyway().apply {
            this.dataSource = dataSource
            isOutOfOrder = true
            setLocations(engine.migrationLocation)

            if (engine == ORACLE) {
                this.setSchemas(schemaName)
                table = "SCHEMA_VERSION"
            }
        }

        flyway.migrate()
    }

    private val DatabaseEngine.migrationLocation
        get(): String = when (this) {
            POSTGRES -> "db/migration-postgres"
            ORACLE -> "db/migration"
        }

    companion object {

        @JvmField
        val DEFAULT_SCHEMA_NAME: String = "DATABASEHOTEL_INSTANCE_DATA"
    }
}
