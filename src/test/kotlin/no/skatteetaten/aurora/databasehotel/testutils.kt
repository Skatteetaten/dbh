package no.skatteetaten.aurora.databasehotel

import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.service.createSchemaNameAndPassword
import no.skatteetaten.aurora.databasehotel.utils.OracleSchemaDeleter

fun DatabaseManager.deleteNonSystemSchemas() = findAllNonSystemSchemas().forEach(this::permanentlyDeleteSchema)

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
