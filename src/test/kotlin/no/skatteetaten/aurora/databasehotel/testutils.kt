package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.utils.OracleSchemaDeleter

fun DatabaseManager.deleteNonSystemSchemas() = findAllNonSystemSchemas().forEach(this::permanentlyDeleteSchema)

fun DatabaseManager.permanentlyDeleteSchema(schema: Schema) = when (this) {
    // The OracleDatabaseManager.deleteSchema is a noop. Actual deletion is handled outside dbh. So, for
    // the tests, we need this special sauce to make the Oracle schemas go away.
    is OracleDatabaseManager -> OracleSchemaDeleter(dataSource).deleteSchema(schema.username)
    else -> deleteSchema(schema.username)
}
