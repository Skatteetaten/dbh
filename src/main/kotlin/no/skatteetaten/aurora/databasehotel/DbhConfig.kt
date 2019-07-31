package no.skatteetaten.aurora.databasehotel

class DbhConfig(databaseConfig: Map<String, Any>) {
    val engine: String by databaseConfig
    val instanceLabels: Map<String, String> by databaseConfig
    val host: String by databaseConfig
    val createSchemaAllowed: Boolean by databaseConfig
    val instanceName: String by databaseConfig
    val username: String by databaseConfig
    val password: String by databaseConfig
    val port: Int by databaseConfig
    val service: String by databaseConfig
    val clientService: String by databaseConfig
    val oracleScriptRequired: Boolean by databaseConfig
}
