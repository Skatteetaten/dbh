package no.skatteetaten.aurora.databasehotel

enum class DatabaseEngine {
    POSTGRES, ORACLE
}

/**
 * Gets the database engine by parsing a typical jdbc string.
 * Examples:
 * - jdbc:oracle:thin:@uil0map-drivein-db01:1521/dbhotel
 * - jdbc:postgresql://localhost:5432/postgres
 */
fun String.toDatabaseEngineFromJdbcUrl(): DatabaseEngine {

    val engineName = ("jdbc:(.*?):.*".toRegex().find(this)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("$this does not appear to be a valid jdbc string"))

    return when (engineName) {
        "postgresql" -> DatabaseEngine.POSTGRES
        "oracle" -> DatabaseEngine.ORACLE
        else -> throw java.lang.IllegalArgumentException("Unsupported database engine $engineName")
    }
}

fun String.toDatabaseEngine(): DatabaseEngine? = try {
    DatabaseEngine.valueOf(this)
} catch (e: IllegalArgumentException) {
    null
}
