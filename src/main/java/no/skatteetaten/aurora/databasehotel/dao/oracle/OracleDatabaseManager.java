package no.skatteetaten.aurora.databasehotel.dao.oracle;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.skatteetaten.aurora.databasehotel.dao.DataAccessException;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport;
import no.skatteetaten.aurora.databasehotel.dao.dto.Schema;

public class OracleDatabaseManager extends DatabaseSupport implements DatabaseManager {

    private static Logger logger = LoggerFactory.getLogger(OracleDatabaseManager.class);

    public OracleDatabaseManager(DataSource dataSource) {

        super(dataSource);
    }

    private static String convertToValid(String schemaName) {

        return schemaName.toUpperCase();
    }

    @Override
    public boolean schemaExists(String schemaName) {

        String query = "SELECT * FROM dba_users u WHERE username=?";
        return getJdbcTemplate().queryForList(query, schemaName).size() == 1;
    }

    @Override
    public String createSchema(String schemaName, String password) {

        String schemaNameValid = convertToValid(schemaName);
        String dataFolder = getDataFolder();
        String[] createSchemaStatements = {
            format("create bigfile tablespace %s datafile '%s/%s.dbf' size 10M autoextend on maxsize 1000G",
                schemaNameValid, dataFolder, schemaNameValid),
            format("create user %s identified by %s default tablespace %s", schemaNameValid, password, schemaNameValid),
            format("grant connect,resource to %s", schemaNameValid),
            format("grant create view to %s", schemaNameValid),
            format("alter user %s quota unlimited on %s", schemaNameValid, schemaNameValid),
            format("alter user %s profile APP_USER", schemaNameValid)
        };
        executeStatements(createSchemaStatements);

        return schemaNameValid;
    }

    /**
     * When changing the password we will also make sure that the account is open (in case it had become locked during
     * a transition for the previous password from active processes).
     *
     * @param schemaName the schema to update the password for
     * @param password   the new password
     */
    @Override
    public void updatePassword(String schemaName, String password) {

        String[] statements = {
            format("ALTER USER %s IDENTIFIED BY %s", schemaName, password),
            format("alter user %s account unlock", schemaName)
        };
        executeStatements(statements);
    }

    @Override
    public Optional<Schema> findSchemaByName(String schemaName) {

        String query = "SELECT * FROM dba_users u WHERE username=?";
        return queryForOne(query, Schema.class, schemaName);
    }

    @Override
    public List<Schema> findAllNonSystemSchemas() {

        String currentUserName = getJdbcTemplate().queryForObject("select user from dual", String.class);
        String query = "SELECT * FROM dba_users u WHERE default_tablespace not in ('SYSTEM', 'SYSAUX', 'USERS') "
            + "and default_tablespace=username and username!=?";
        return queryForMany(query, Schema.class, currentUserName);
    }

    @Override
    public void deleteSchema(String schemaName) {

        disconnectAllUsers(schemaName);

        String[] dropSchemaStatements = {
            format("DROP USER %s cascade", schemaName),
            format("DROP TABLESPACE %s INCLUDING CONTENTS AND DATAFILES", schemaName)
        };
        executeStatementsOnlyLogErrors(dropSchemaStatements);
    }

    /**
     * Will try to disconnect all active users and connections against a specified schema.
     * <p>
     * Disconnecting all users for a schema is actually more flaky and unreliable than it ideally should be, so this
     * method will try a few times before giving up.
     *
     * @param schemaName the name of the schema to disconnect users from
     * @throws DataAccessException if all sessions could not be closed by the nth attempt.
     */
    private void disconnectAllUsers(String schemaName) throws DataAccessException {

        int maxNumberOfDisconnectAttempts = 10;
        // We may not be able to disconnect all users on our first try, so try a few times before moving on.
        for (int attempt = 1; attempt <= maxNumberOfDisconnectAttempts; attempt++) {
            List<Map<String, Object>> activeSessions =
                getJdbcTemplate().queryForList("SELECT s.sid, s.serial# FROM v$session s where username=?", schemaName);
            if (activeSessions.isEmpty()) {
                // Only when there are no active sessions are we actually successfully finished.
                return;
            }
            activeSessions.forEach(session -> {
                Object sid = session.get("sid");
                Object serial = session.get("serial#");
                String[] statements = {
                    format("ALTER SYSTEM KILL SESSION '%s, %s' IMMEDIATE", sid, serial),
                    // KILL SESSION by it self does not always do the trick...
                    format("ALTER SYSTEM DISCONNECT SESSION '%s, %s' IMMEDIATE", sid, serial)
                };
                executeStatementsOnlyLogErrors(statements);
            });
            if (attempt >= 2) {
                // If we are beyond the first attempt, lets wait a little to let the database server catch its breath.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        }
        throw new DataAccessException(format("Unable to disconnect all users from schema=%s by "
            + "the %d attempt. Gave up.", schemaName, maxNumberOfDisconnectAttempts));
    }

    private String getDataFolder() {

        String dataFolderQuery =
            "SELECT SUBSTR(FILE_NAME, 1, INSTR(FILE_NAME, '/', -1) -1) as DATA_FOLDER FROM DBA_DATA_FILES where "
                + "TABLESPACE_NAME='SYSTEM'";
        return getJdbcTemplate().queryForObject(dataFolderQuery, String.class);
    }

    private void executeStatementsOnlyLogErrors(String... statements) {

        for (String statement : statements) {
            try {
                getJdbcTemplate().execute(statement);
            } catch (Exception e) {
                logger.warn(format("Error executing statement=[%s]", statement), e);
            }
        }
    }
}
