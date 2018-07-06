package no.skatteetaten.aurora.databasehotel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Janitor {

    private static Logger LOG = LoggerFactory.getLogger(Janitor.class);
    private final boolean dropAllowed;

    private DatabaseHotelAdminService databaseHotelAdminService;

    public Janitor(
        DatabaseHotelAdminService databaseHotelAdminService,
        @Value("${databaseConfig.dropAllowed}") boolean dropAllowed
    ) {

        this.databaseHotelAdminService = databaseHotelAdminService;
        this.dropAllowed = dropAllowed;
    }

    @Scheduled(fixedDelay = 3600 * 1000, initialDelay = 60 * 1000)
    public void deleteOldUnusedSchemas() {

        if (!dropAllowed) {
            return;
        }
        LOG.info("Periodic deletion of old unused schemas");
        databaseHotelAdminService.findAllDatabaseInstances().forEach(DatabaseInstance::deleteUnusedSchemas);
    }
}
