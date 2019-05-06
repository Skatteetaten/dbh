package no.skatteetaten.aurora.databasehotel.dao.postgres

import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.dao.dto.Schema
import org.springframework.jdbc.core.queryForObject
import java.util.Optional
import javax.sql.DataSource

/**
 *
 */
class PostgresDatabaseManager(dataSource: DataSource) : DatabaseSupport(dataSource), DatabaseManager {

    override fun createSchema(schemaName: String, password: String): String {

        val safeName = schemaName.toSafe()
        executeStatements(
            "create user $safeName with password '$password'",
            "create database $safeName",
            "GRANT CREATE ON DATABASE $safeName TO $safeName",
            "GRANT CONNECT ON DATABASE $safeName TO $safeName",
            "grant app_user to $safeName"
        )
        return safeName
    }

    override fun updatePassword(schemaName: String, password: String) {
        executeStatements("ALTER USER ${schemaName.toSafe()} WITH PASSWORD '$password'")
    }

    override fun findSchemaByName(schemaName: String): Optional<Schema> {
        val query = "SELECT datname as username, now() as created, now() as lastLogin FROM pg_database WHERE datname=?"
        return queryForOne(query, Schema::class.java, schemaName.toSafe())
    }

    override fun findAllNonSystemSchemas(): List<Schema> {
        val query = "SELECT datname as username, now() as created, now() as lastLogin FROM pg_database WHERE datistemplate = false and datname not in ('postgres')"
        return queryForMany(query, Schema::class.java)
    }

    override fun deleteSchema(schemaName: String) {
        val safeName = schemaName.toSafe()
        executeStatements(
            "UPDATE pg_database SET datallowconn = 'false' WHERE datname = '$safeName'",
            "ALTER DATABASE $safeName CONNECTION LIMIT 1",
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$safeName'",
            "drop database $safeName",
            "drop role $safeName"
        )
    }

    override fun schemaExists(schemaName: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM pg_database WHERE datname=?)",
            arrayOf(schemaName.toSafe())
        )!!

    /**
     * Converts the name of a schema to a string that is safe to use as a database name for Postgres.
     */
    private fun String.toSafe() = this.toLowerCase()
}