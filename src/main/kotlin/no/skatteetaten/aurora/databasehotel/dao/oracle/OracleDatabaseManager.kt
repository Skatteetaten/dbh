package no.skatteetaten.aurora.databasehotel.dao.oracle

import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.toSchema
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.dao.EmptyResultDataAccessException
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

class OracleDatabaseManager(dataSource: DataSource) : DatabaseSupport(dataSource), DatabaseManager {

    private val dataFolder: String?
        get() {

            val dataFolderQuery =
                "SELECT SUBSTR(FILE_NAME, 1, INSTR(FILE_NAME, '/', -1) -1) as DATA_FOLDER FROM DBA_DATA_FILES where " + "TABLESPACE_NAME='SYSTEM'"
            return jdbcTemplate.queryForObject(dataFolderQuery, String::class.java)
        }

    override fun schemaExists(schemaName: String): Boolean {

        val query = "SELECT * FROM dba_users u WHERE username=?"
        return jdbcTemplate.queryForList(query, schemaName).size == 1
    }

    override fun createSchema(schemaName: String, password: String): String {

        val schemaNameValid = convertToValid(schemaName)
        val dataFolder = dataFolder
        val createSchemaStatements = arrayOf(
            "create bigfile tablespace $schemaNameValid datafile '$dataFolder/$schemaNameValid.dbf' size 10M autoextend on maxsize 1000G",
            "create user $schemaNameValid identified by $password default tablespace $schemaNameValid",
            "grant connect,resource to $schemaNameValid",
            "grant create view to $schemaNameValid",
            "alter user $schemaNameValid quota unlimited on $schemaNameValid",
            "alter user $schemaNameValid profile APP_USER"
        )

        try {
            executeStatements(*createSchemaStatements)
            return schemaNameValid
        } catch (e: org.springframework.dao.DataAccessException) {
            throw DataAccessException(e.getDbMessage(), e)
        }
    }

    /**
     * When changing the password we will also make sure that the account is open (in case it had become locked during
     * a transition for the previous password from active processes).
     *
     * @param schemaName the schema to update the password for
     * @param password the new password
     */
    override fun updatePassword(schemaName: String, password: String) {

        val statements = arrayOf(
            "ALTER USER $schemaName IDENTIFIED BY $password",
            "alter user $schemaName account unlock"
        )
        executeStatements(*statements)
    }

    override fun findSchemaByName(schemaName: String): Schema? {
        val query = "SELECT username, created, last_login as lastLogin FROM dba_users u WHERE username=?"
        return try {
            jdbcTemplate.queryForObject(query, toSchema, schemaName)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findAllNonSystemSchemas(): List<Schema> {

        val currentUserName = jdbcTemplate.queryForObject("select user from dual", String::class.java)
        val query = (
            """SELECT username, created, last_login as lastLogin FROM dba_users u WHERE 
            default_tablespace not in ('SYSTEM', 'SYSAUX', 'USERS', 'MAPTEST', 'AOS_API_USER', 'RESIDENTS') 
            and default_tablespace=username and username!=?""".trimIndent())
        return jdbcTemplate.query(query, toSchema, currentUserName)
    }

    override fun deleteSchema(schemaName: String) {

        disconnectAllUsers(schemaName)

        val dropSchemaStatements = arrayOf(
            "DROP USER $schemaName cascade",
            "DROP TABLESPACE $schemaName INCLUDING CONTENTS AND DATAFILES"
        )
        executeStatementsOnlyLogErrors(*dropSchemaStatements)
    }

    /**
     * Will try to disconnect all active users and connections against a specified schema.
     *
     *
     * Disconnecting all users for a schema is actually more flaky and unreliable than it ideally should be, so this
     * method will try a few times before giving up.
     *
     * @param schemaName the name of the schema to disconnect users from
     * @throws DataAccessException if all sessions could not be closed by the nth attempt.
     */
    @Throws(DataAccessException::class)
    private fun disconnectAllUsers(schemaName: String) {

        val maxNumberOfDisconnectAttempts = 10
        // We may not be able to disconnect all users on our first try, so try a few times before moving on.
        for (attempt in 1..maxNumberOfDisconnectAttempts) {
            val activeSessions =
                jdbcTemplate.queryForList("SELECT s.sid, s.serial# FROM v\$session s where username=?", schemaName)
            if (activeSessions.isEmpty()) {
                // Only when there are no active sessions are we actually successfully finished.
                return
            }
            activeSessions.forEach { session ->
                val sid = session["sid"]
                val serial = session["serial#"]
                val statements = arrayOf(
                    "ALTER SYSTEM KILL SESSION '$sid, $serial' IMMEDIATE",
                    // KILL SESSION by it self does not always do the trick...
                    "ALTER SYSTEM DISCONNECT SESSION '$sid, $serial' IMMEDIATE"
                )
                executeStatementsOnlyLogErrors(*statements)
            }
            if (attempt >= 2) {
                // If we are beyond the first attempt, lets wait a little to let the database server catch its breath.
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                }
            }
        }
        throw DataAccessException(
            "Unable to disconnect all users from schema=$schemaName by the $maxNumberOfDisconnectAttempts attempt. Gave up."
        )
    }

    private fun executeStatementsOnlyLogErrors(vararg statements: String) {

        for (statement in statements) {
            try {
                jdbcTemplate.execute(statement)
            } catch (e: Exception) {
                logger.warn("Error executing statement=[$statement]", e)
            }
        }
    }

    companion object {

        private fun convertToValid(schemaName: String): String {
            return schemaName.toUpperCase()
        }
    }
}

fun Throwable.getDbMessage(): String {
    val dbMessage = ExceptionUtils.getThrowableList(this)
        .filterIsInstance<SQLException>()
        .map { it.message }
        .firstOrNull() ?: ""

    return "${this.message} $dbMessage"
}