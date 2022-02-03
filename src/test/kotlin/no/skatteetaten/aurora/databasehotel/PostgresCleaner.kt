package no.skatteetaten.aurora.databasehotel

import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.domain.User
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import javax.sql.DataSource

class PostgresCleaner(dataSource: DataSource) {
    private val adminDBManager = PostgresDatabaseManager(dataSource)
    private val donotdelete = setOf("postgres", "hadmin", "databasehotel_instance_data")

    fun cleanInstanceSchemas(databaseInstances: Set<DatabaseInstance>) {
        val suppressed = mutableListOf<Exception>()
        databaseInstances.forEach { instance ->
            val s = instance.findAllSchemas(ignoreActiveFilter = true).filter { !donotdelete.contains(it.name) }
            val dbm = instance.databaseManager as PostgresDatabaseManager
            s.forEach { schema ->
                try {
                    dbm.permanentlyDeleteSchema(Schema(schema.name))
                } catch (e: Exception) {
                    suppressed.add(e)
                }
            }
            try {
                val schemaName = (dbm.dataSource as HikariDataSource).username
                if (!donotdelete.contains(schemaName)) {
                    terminateBackend(schemaName, dbm)
                    dropDatabaseAndRole(schemaName)
                }
            } catch (e: Exception) {
                suppressed.add(e)
            }
        }
        if (suppressed.isNotEmpty()) {
            throw PostgresCleanupException(
                message = "error(s) occurred while cleaning up test schemas and roles",
                suppressed = suppressed
            )
        }
    }

    fun cleanDataSourceSchema(schemaDataSource: HikariDataSource) {
        val schemaName = schemaDataSource.username
        try {
            schemaDataSource.close()
            dropDatabaseAndRole(schemaName)
        } catch (e: Exception) {
            throw PostgresCleanupException(message = "error occurred while cleaning up test schema", cause = e)
        }
    }

    fun cleanInstanceSchema(instance: DatabaseInstance, config: PostgresConfig) {
        val terminateBackendExceptions = mutableMapOf<String, Exception>()
        val suppressed = mutableListOf<Exception>()

        val schemas = instance.findAllSchemas(ignoreActiveFilter = true).filter { !donotdelete.contains(it.name) }
        schemas.forEach { schema ->
            schema.users.forEach { user ->
                try {
                    val dbm = createDatabaseManager(user, config)
                    terminateBackend(schema.name, dbm)
                } catch (e: Exception) {
                    terminateBackendExceptions[user.name] = e
                }
            }
            try {
                if (!donotdelete.contains(schema.name)) {
                    dropDatabaseAndRole(schema.name)
                    // if drop database and role ok, ignore errors from terminating backend
                    terminateBackendExceptions.remove(schema.name)
                }
            } catch (e: Exception) {
                suppressed.add(e)
            }
        }
        terminateBackendExceptions.forEach { suppressed.add(it.value) }
        if (suppressed.isNotEmpty()) {
            throw PostgresCleanupException("error occurred while cleaning instance schema", suppressed = suppressed)
        }
    }

    private fun terminateBackend(schemaName: String, dbm: PostgresDatabaseManager) {
        dbm.executeStatements(
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$schemaName'"
        )
    }

    private fun dropDatabaseAndRole(schemaName: String) {
        adminDBManager.executeStatements(
            "drop database $schemaName",
            "drop role $schemaName"
        )
    }

    private fun createDatabaseManager(user: User, config: PostgresConfig): PostgresDatabaseManager {
        val ds = DataSourceUtils.createDataSource(
            PostgresJdbcUrlBuilder().create(config.host, config.port.toInt(), "postgres"),
            user.name,
            user.password,
            1
        )
        return PostgresDatabaseManager(ds)
    }
}

class PostgresCleanupException(message: String, cause: Exception? = null, suppressed: List<Exception> = emptyList()) :
    RuntimeException(message, cause) {
    init {
        suppressed.forEach { this.addSuppressed(it) }
    }
}
