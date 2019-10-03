package no.skatteetaten.aurora.databasehotel

import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.service.createSchemaNameAndPassword
import no.skatteetaten.aurora.databasehotel.utils.OracleSchemaDeleter

fun DatabaseManager.deleteNonSystemSchemas() {
    val attempts = 10
    for (i in (1..attempts)) {
        try {
            findAllNonSystemSchemas().forEach(this::permanentlyDeleteSchema)
            break
        } catch (e: Exception) {
            // Because some of the schemas/roles that are created are superusers that are used to create
            // other schemas, deleting those roles before the schemas that were created by them may fail. The fix for
            // this is to try again and it will most likely succeed. If not, there is something other that is wrong and
            // it is ok to fail.
            if (i == attempts) throw e
        }
    }
}

fun DatabaseManager.permanentlyDeleteSchema(schema: Schema) = when (this) {
    // The OracleDatabaseManager.deleteSchema is a noop. Actual deletion is handled outside dbh. So, for
    // the tests, we need this special sauce to make the Oracle schemas go away.
    is OracleDatabaseManager -> OracleSchemaDeleter(dataSource).deleteSchema(schema.username)
    else -> deleteSchema(schema.username)
}

fun createPostgresSchema(managerDataSource: HikariDataSource): HikariDataSource {
    val manager = PostgresDatabaseManager(managerDataSource)
    val (username, password) = createSchemaNameAndPassword()
    val schemaName = manager.createSchema(username, password)
    val jdbcUrl = managerDataSource.jdbcUrl.replace(Regex("/[a-z]+$"), "/$schemaName")
    return DataSourceUtils.createDataSource(jdbcUrl, schemaName, password)
}

fun createOracleSchema(mangerDataSource: HikariDataSource): HikariDataSource {
    val manager = OracleDatabaseManager(mangerDataSource)
    val (username, password) = createSchemaNameAndPassword()
    val schemaName = manager.createSchema(username, password)
    return DataSourceUtils.createDataSource(mangerDataSource.jdbcUrl, schemaName, password)
}
