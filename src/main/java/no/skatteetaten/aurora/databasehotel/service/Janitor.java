package no.skatteetaten.aurora.databasehotel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Janitor {

    private static Logger LOG = LoggerFactory.getLogger(Janitor.class);

    private DatabaseHotelAdminService databaseHotelAdminService;

    public Janitor(DatabaseHotelAdminService databaseHotelAdminService) {
        this.databaseHotelAdminService = databaseHotelAdminService;
    }

    @Scheduled(fixedDelay = 3600 * 1000, initialDelay = 60 * 1000)
    public void deleteOldUnusedSchemas() {

        LOG.info("Periodic deletion of old unused schemas");
        databaseHotelAdminService.findAllDatabaseInstances().forEach(DatabaseInstance::deleteUnusedSchemas);
    }
}
