package no.skatteetaten.aurora.databasehotel.dao.postgres

import javax.sql.DataSource
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.toSchema
import org.springframework.jdbc.core.queryForObject

class PostgresDatabaseManager(dataSource: DataSource) : DatabaseSupport(dataSource), DatabaseManager {

    override fun createSchema(schemaName: String, password: String): String {

        val safeName = schemaName.toSafe()
        executeStatements(
            """DO ${'$'}${'$'}
                BEGIN
                  CREATE ROLE app_user WITH NOLOGIN;
                  EXCEPTION WHEN OTHERS THEN
                  RAISE NOTICE 'not creating role app_user -- it already exists';
                END
                ${'$'}${'$'};""".trimIndent(),
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

    override fun findSchemaByName(schemaName: String): Schema? {
        val query = "SELECT datname as username, null as lastLogin FROM pg_database WHERE datname=?"
        return jdbcTemplate.queryForObject(query, toSchema, schemaName.toSafe())
    }

    override fun findAllNonSystemSchemas(): List<Schema> {
        val query =
            "SELECT datname as username, null as created, null as lastLogin FROM pg_database WHERE datistemplate = false and datname not in ('postgres')"
        return jdbcTemplate.query(query, toSchema)
    }

    override fun deleteSchema(schemaName: String) {
        val safeName = schemaName.toSafe()
        executeStatements(
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

    override fun getMaxTablespaces(): Int? {
        // Not implemented for postgres
        return null
    }

    override fun getUsedTablespaces(): Int? {
        // Not implemented for postgres
        return null
    }

    /**
     * Converts the name of a schema to a string that is safe to use as a database name for Postgres.
     */
    private fun String.toSafe() = this.toLowerCase()
}
