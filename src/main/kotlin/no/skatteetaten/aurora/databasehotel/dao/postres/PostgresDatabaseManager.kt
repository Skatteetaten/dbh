package no.skatteetaten.aurora.databasehotel.dao.postres

import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.dao.dto.Schema
import org.springframework.jdbc.core.queryForObject
import java.util.Optional
import javax.sql.DataSource

class PostgresDatabaseManager(dataSource: DataSource) : DatabaseSupport(dataSource), DatabaseManager {
    override fun getCurrentUserName(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createSchema(schemaName: String, password: String): String {

        val s = schemaName.s()
        executeStatements(
            "create user $schemaName with password '$password'",
            "create database $s with owner $s"
        )
        return s;
    }

    private fun String.s() = this.toLowerCase()

    override fun updatePassword(schemaName: String, password: String) {
        executeStatements("ALTER USER ${schemaName.s()} WITH PASSWORD '$password'")
    }

    override fun findSchemaByName(name: String): Optional<Schema> {
        val query = "SELECT datname as username, now() as created, now() as lastLogin FROM pg_database WHERE datname=?"
        return queryForOne(query, Schema::class.java, name)
    }

    override fun findAllNonSystemSchemas(): List<Schema> {
        val query = "SELECT datname as username, now() as created, now() as lastLogin FROM pg_database"
        return queryForMany(query, Schema::class.java)
    }

    override fun deleteSchema(schemaName: String) {
        val s = schemaName.s()
        executeStatements(
            "UPDATE pg_database SET datallowconn = 'false' WHERE datname = '$s'",
            "ALTER DATABASE $s CONNECTION LIMIT 1",
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$s'",
            "drop database $s",
            "drop role $s"
        )
    }

    override fun schemaExists(schemaName: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM pg_database WHERE datname=?)",
            arrayOf(schemaName.s())
        )!!
}