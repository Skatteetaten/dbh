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

    @Scheduled(fixedDelay = 3600L * 1000, initialDelay = 60L * 1000)
    fun deleteStaleSchemasByCooldown() {

        if (!dropAllowed) return

        logger.info("Periodic run of janitor")
        databaseHotelAdminService.findAllDatabaseInstances().forEach {
            it.deactivateStaleSchemas()
            it.deleteSchemasWithExpiredCooldowns()
        }
    }
}
