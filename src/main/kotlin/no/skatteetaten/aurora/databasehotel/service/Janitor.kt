package no.skatteetaten.aurora.databasehotel.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class Janitor(
    private val databaseHotelAdminService: DatabaseHotelAdminService,
    @param:Value("\${database-config.dropAllowed}") private val dropAllowed: Boolean
) {

    @Scheduled(fixedDelay = (3600 * 1000).toLong(), initialDelay = (60 * 1000).toLong())
    fun deleteStaleSchemasByCooldown() {

        if (!dropAllowed) return

        logger.info("Periodic check for stale database schemas")
        databaseHotelAdminService.findAllDatabaseInstances()
            .forEach { it.deleteStaleSchemasByCooldown() }
    }
}
