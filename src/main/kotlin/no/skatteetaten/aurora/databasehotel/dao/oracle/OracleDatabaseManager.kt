package no.skatteetaten.aurora.databasehotel.dao.oracle

import java.sql.SQLException
import javax.sql.DataSource
import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.toSchema
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.dao.EmptyResultDataAccessException

class OracleDatabaseManager(dataSource: DataSource) : DatabaseSupport(dataSource), DatabaseManager {

    private val dataFolder: String?
        get() {

            val dataFolderQuery =
                "SELECT SUBSTR(FILE_NAME, 1, INSTR(FILE_NAME, '/', -1) -1) as DATA_FOLDER FROM DBA_DATA_FILES where " + "TABLESPACE_NAME='SYSTEM' " + "and rownum=1"
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
        val query = "SELECT username, last_login as lastLogin FROM dba_users u WHERE username=?"
        return try {
            jdbcTemplate.queryForObject(query, toSchema, schemaName)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findAllNonSystemSchemas(): List<Schema> {

        val currentUserName = jdbcTemplate.queryForObject("select user from dual", String::class.java)
        val query = (
            """SELECT username, last_login as lastLogin FROM dba_users u WHERE 
            default_tablespace not in ('SYSTEM', 'SYSAUX', 'USERS', 'MAPTEST', 'AOS_API_USER', 'RESIDENTS') 
            and default_tablespace=username and username!=?""".trimIndent())
        return jdbcTemplate.query(query, toSchema, currentUserName)
    }

    override fun deleteSchema(schemaName: String) {

        // NOOP
    }

    override fun getMaxTablespaces(): Int? {
        val query = "SELECT VALUE FROM v\$parameter WHERE NAME='db_files'"
        return jdbcTemplate.queryForObject(query, Int::class.java)
    }

    override fun getUsedTablespaces(): Int? {
        val query = "SELECT count(*) FROM v\$tablespace"
        return jdbcTemplate.queryForObject(query, Int::class.java)
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
