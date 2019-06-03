package no.skatteetaten.aurora.databasehotel.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class Janitor(
    private val databaseHotelAdminService: DatabaseHotelAdminService,
    @param:Value("\${database-config.dropAllowed}") private val dropAllowed: Boolean
) {

    private val LOG = LoggerFactory.getLogger(Janitor::class.java)

    @Scheduled(fixedDelay = (3600 * 1000).toLong(), initialDelay = (60 * 1000).toLong())
    fun deleteOldUnusedSchemas() {

        if (!dropAllowed) return

        LOG.info("Periodic deletion of old unused schemas")
        databaseHotelAdminService.findAllDatabaseInstances()
            .forEach { it.pruneSchemasForDeletion() }
    }
}
