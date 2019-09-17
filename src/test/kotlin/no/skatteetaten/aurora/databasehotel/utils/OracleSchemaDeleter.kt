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
}
