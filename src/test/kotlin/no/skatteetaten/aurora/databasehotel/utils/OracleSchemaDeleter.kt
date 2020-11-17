package no.skatteetaten.aurora.databasehotel.utils

import javax.sql.DataSource
import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport

private val logger = KotlinLogging.logger {}

class OracleSchemaDeleter(dataSource: DataSource) : DatabaseSupport(dataSource) {

    fun deleteSchema(schemaName: String) {

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
        (1..maxNumberOfDisconnectAttempts).forEach { attempt ->
            val activeSessions = getActiveSessions(schemaName)
            // Only when there are no active sessions are we actually successfully finished.
            if (activeSessions.isEmpty()) return
            killSessions(activeSessions)
            // If we are beyond the first attempt, lets wait a little to let the database server catch its breath.
            if (attempt >= 2) Thread.sleep(1000)
        }
        throw DataAccessException(
            "Unable to disconnect all users from schema=$schemaName by the $maxNumberOfDisconnectAttempts attempt. Gave up."
        )
    }

    private fun killSessions(activeSessions: List<MutableMap<String, Any>>) {
        activeSessions.forEach { session ->
            val sid = session["sid"]
            val serial = session["serial#"]
            val statements = arrayOf(
                // See test/resources/kill_dev_session.sql
                "BEGIN sys.kill_dev_session($sid, $serial); END;"
            )
            executeStatementsOnlyLogErrors(*statements)
        }
    }

    private fun getActiveSessions(schemaName: String) =
        jdbcTemplate.queryForList("SELECT s.sid, s.serial# FROM v\$session s where username=? and not status='KILLED'", schemaName)

    private fun executeStatementsOnlyLogErrors(vararg statements: String) {

        for (statement in statements) try {
            jdbcTemplate.execute(statement)
        } catch (e: Exception) {
            logger.warn("Error executing statement=[$statement]", e)
        }
    }
}
