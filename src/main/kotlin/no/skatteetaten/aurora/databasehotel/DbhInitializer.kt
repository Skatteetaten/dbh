package no.skatteetaten.aurora.databasehotel

import com.google.common.collect.Lists
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import no.skatteetaten.aurora.databasehotel.service.ExternalSchemaManager
import no.skatteetaten.aurora.databasehotel.utils.MapUtils.get
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.lang.String.format

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
        while (!databasesConfig.isEmpty()) {
            val i = databasesConfig.iterator()
            while (i.hasNext()) {
                val databaseConfig = i.next()
                try {
                    registerDatabase(databaseConfig)
                    i.remove()
                } catch (e: Exception) {
                    val host = get<String, String>(databaseConfig, "host")
                    LOGGER.warn(format("Unable to connect to %s - will try again later", host))
                    LOGGER.debug(format("Unable to connect to %s - will try again later", host), e)
                    Thread.sleep(retryDelay.toLong())
                }
            }
        }
        val defaultDatabaseInstance = databaseHotelAdminService.findDefaultDatabaseInstance()
        val databaseHotelDataDao = defaultDatabaseInstance.databaseHotelDataDao
        val externalSchemaManager = ExternalSchemaManager(databaseHotelDataDao)
        databaseHotelAdminService.registerExternalSchemaManager(externalSchemaManager)
        LOGGER.info("Registered ExternalSchemaManager")
    }

    private fun registerDatabase(databaseConfig: Map<String, Any>) {

        val engine: String = databaseConfig.get2("engine")

        val host: String = databaseConfig.get2("host")
        val createSchemaAllowed = databaseConfig.get2("createSchemaAllowed", "true").toBoolean()
        val instanceName: String = databaseConfig.get2("instanceName")
        val username: String = databaseConfig.get2("username")
        val password: String = databaseConfig.get2("password")
        when (engine) {
            "postgres" -> {
                databaseHotelAdminService.registerPostgresDatabaseInstance(
                    instanceName,
                    host,
                    5432,
                    username,
                    password,
                    createSchemaAllowed
                )
            }
            "oracle" -> {
                val oracleScriptRequired: Boolean = databaseConfig.get2("oracleScriptRequired", "false").toBoolean()
                val service: String = databaseConfig.get2("service")
                val clientService: String = databaseConfig.get2("clientService")
                databaseHotelAdminService.registerOracleDatabaseInstance(
                    instanceName,
                    host,
                    service,
                    username,
                    password,
                    clientService,
                    createSchemaAllowed,
                    oracleScriptRequired
                )
            }
        }
        LOGGER.info("Registered host [{}]", host)
    }

    private inline fun <reified T> Map<String, *>.get2(key: String, default: T? = null): T =
        this.getOrDefault(key, default) as T

    companion object {

        private val LOGGER = LoggerFactory.getLogger(DbhInitializer::class.java)
    }
}
