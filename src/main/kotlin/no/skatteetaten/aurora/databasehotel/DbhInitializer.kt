package no.skatteetaten.aurora.databasehotel

import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import no.skatteetaten.aurora.databasehotel.service.ExternalSchemaManager
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class DbhInitializer(
    private val databaseHotelAdminService: DatabaseHotelAdminService,
    private val configuration: DbhConfiguration
) {

    @Async
    fun configure() {

        val databasesConfig = configuration.databases.toMutableList()

        // Iterate over all the database configurations, removing them one by one as we manage to register them
        // (in practice being able to connect to them). For each pass of the configurations, sleep for a while before
        // reiterating the ones we did not manage to connect to in the last pass.
        while (databasesConfig.isNotEmpty()) {
            val i = databasesConfig.iterator()
            while (i.hasNext()) {
                val databaseConfig = i.next()
                try {
                    registerDatabase(databaseConfig)
                    i.remove()
                } catch (e: Exception) {
                    val host = databaseConfig["host"]
                    logger.warn("Unable to connect to $host - will try again later (${e.message})")
                    Thread.sleep(configuration.retryDelay.toLong())
                }
            }
        }
    }

    private fun registerDatabase(databaseConfig: Map<String, Any>) {

        val engine: String = databaseConfig.typedGet("engine")

        val instanceLabels: Map<String, String> = databaseConfig.typedGet("labels", emptyMap())
        val host: String = databaseConfig.typedGet("host")
        val createSchemaAllowed: Boolean = databaseConfig.typedGet("createSchemaAllowed", true)
        val instanceName: String = databaseConfig.typedGet("instanceName")
        val username: String = databaseConfig.typedGet("username")
        val password: String = databaseConfig.typedGet("password")

        logger.info("Registering host [{}]", host)

        val instance = when (engine) {
            "postgres" -> {
                val port: Int = databaseConfig.typedGet("port")
                databaseHotelAdminService.registerPostgresDatabaseInstance(
                    instanceName,
                    host,
                    port,
                    username,
                    password,
                    createSchemaAllowed,
                    instanceLabels
                )
            }
            "oracle" -> {
                val oracleScriptRequired: Boolean = databaseConfig.typedGet("oracleScriptRequired", false)
                val service: String = databaseConfig.typedGet("service")
                val clientService: String = databaseConfig.typedGet("clientService")
                databaseHotelAdminService.registerOracleDatabaseInstance(
                    instanceName,
                    host,
                    1521,
                    service,
                    username,
                    password,
                    clientService,
                    createSchemaAllowed,
                    oracleScriptRequired,
                    instanceLabels
                )
            }
            else -> throw IllegalArgumentException("Unknown engine $engine")
        }
        logger.info("Registered host [{}]", host)

        if (instance.instanceName == configuration.defaultInstanceName) {
            val databaseHotelDataDao = instance.databaseHotelDataDao
            val externalSchemaManager = ExternalSchemaManager(databaseHotelDataDao)
            databaseHotelAdminService.externalSchemaManager = externalSchemaManager
            logger.info("Registered host [{}] as ExternalSchemaManager", host)
        }
    }

    private inline fun <reified T> Map<String, *>.typedGet(key: String, default: T? = null): T {
        val value: Any = this.getOrDefault(key, default)
            ?: throw IllegalArgumentException("No value for key $key")
        return when (T::class) {
            Boolean::class -> value.toString().toBoolean() as T
            Int::class -> value.toString().toInt() as T
            else -> value as T
        }
    }
}
