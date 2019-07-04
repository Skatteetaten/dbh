package no.skatteetaten.aurora.databasehotel

import com.google.common.collect.Lists
import mu.KotlinLogging
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import no.skatteetaten.aurora.databasehotel.service.ExternalSchemaManager
import no.skatteetaten.aurora.databasehotel.utils.MapUtils.get
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class DbhInitializer(
    private val databaseHotelAdminService: DatabaseHotelAdminService,
    private val configuration: DbhConfiguration,
    @Value("\${database-config.retryDelay}")
    val retryDelay: Int
) {

    @Async
    fun configure() {

        val databasesConfig = Lists.newArrayList(configuration.databasesConfig)

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
                    val host: String = get(databaseConfig, "host")
                    logger.warn("Unable to connect to $host - will try again later (${e.message})")
                    Thread.sleep(retryDelay.toLong())
                }
            }
        }
        val defaultDatabaseInstance = databaseHotelAdminService.findDefaultDatabaseInstance()
        val databaseHotelDataDao = defaultDatabaseInstance.databaseHotelDataDao
        val externalSchemaManager = ExternalSchemaManager(databaseHotelDataDao)
        databaseHotelAdminService.externalSchemaManager = externalSchemaManager
        logger.info("Registered ExternalSchemaManager")
    }

    private fun registerDatabase(databaseConfig: Map<String, Any>) {

        val engine: String = databaseConfig.typedGet("engine")

        val instanceLabels: Map<String, String> = databaseConfig.typedGet("labels", emptyMap())
        val host: String = databaseConfig.typedGet("host")
        val createSchemaAllowed = databaseConfig.typedGet("createSchemaAllowed", "true").toBoolean()
        val instanceName: String = databaseConfig.typedGet("instanceName")
        val username: String = databaseConfig.typedGet("username")
        val password: String = databaseConfig.typedGet("password")
        when (engine) {
            "postgres" -> {
                val port: Int = (databaseConfig.typedGet<String>("port").toInt())
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
                val oracleScriptRequired: Boolean = databaseConfig.typedGet("oracleScriptRequired", "false").toBoolean()
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
        }
        logger.info("Registered host [{}]", host)
    }

    private inline fun <reified T> Map<String, *>.typedGet(key: String, default: T? = null): T =
        this.getOrDefault(key, default) as T
}
