package no.skatteetaten.aurora.databasehotel

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

        val databasesConfig = configuration.databasesConfig.toMutableList()

        // Iterate over all the database configurations, removing them one by one as we manage to register them
        // (in practice being able to connect to them). For each pass of the configurations, sleep for a while before
        // reiterating the ones we did not manage to connect to in the last pass.
        while (databasesConfig.isNotEmpty()) {
            val i = databasesConfig.iterator()
            while (i.hasNext()) {
                val dbhConfig = DbhConfig(i.next())
                try {
                    databaseHotelAdminService.registerDatabaseInstance(dbhConfig)
                    i.remove()
                } catch (e: Exception) {
                    logger.warn("Unable to connect to ${dbhConfig.host} - will try again later (${e.message})")
                    Thread.sleep(retryDelay.toLong())
                }
            }
        }
        val databaseHotelDataDao = databaseHotelAdminService.findDefaultDatabaseInstance().databaseHotelDataDao
        databaseHotelAdminService.externalSchemaManager = ExternalSchemaManager(databaseHotelDataDao)
        logger.info("Registered ExternalSchemaManager")
    }
}
