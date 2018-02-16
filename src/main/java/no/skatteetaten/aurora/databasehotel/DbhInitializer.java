package no.skatteetaten.aurora.databasehotel;

import static java.lang.String.format;

import static no.skatteetaten.aurora.databasehotel.utils.MapUtils.get;
import static no.skatteetaten.aurora.databasehotel.utils.MapUtils.maybeGet;
import static no.skatteetaten.aurora.databasehotel.utils.MapUtils.maybeGetAsBoolean;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService;
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance;
import no.skatteetaten.aurora.databasehotel.service.ExternalSchemaManager;

@Component
public class DbhInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbhInitializer.class);

    private DatabaseHotelAdminService databaseHotelAdminService;

    private DbhConfiguration configuration;

    @Value("${databaseConfig.retryDelay}")
    private Integer retryDelay;

    @Autowired
    public DbhInitializer(DatabaseHotelAdminService databaseHotelAdminService, DbhConfiguration configuration) {

        this.databaseHotelAdminService = databaseHotelAdminService;
        this.configuration = configuration;
    }

    public DbhInitializer(DatabaseHotelAdminService databaseHotelAdminService, DbhConfiguration configuration,
        int retryDelay) {

        this(databaseHotelAdminService, configuration);
        this.retryDelay = retryDelay;
    }

    @Async
    public void configure() throws InterruptedException {

        List<Map<String, Object>> databasesConfig = Lists.newArrayList(configuration.getDatabasesConfig());

        // Iterate over all the database configurations, removing them one by one as we manage to register them
        // (in practice being able to connect to them). For each pass of the configurations, sleep for a while before
        // reiterating the ones we did not manage to connect to in the last pass.
        while (!databasesConfig.isEmpty()) {
            Iterator<Map<String, Object>> i = databasesConfig.iterator();
            while (i.hasNext()) {
                Map<String, Object> databaseConfig = i.next();
                try {
                    registerDatabase(databaseConfig);
                    i.remove();
                } catch (Exception e) {
                    String host = get(databaseConfig, "host");
                    LOGGER.warn(format("Unable to connect to %s - will try again later", host));
                    LOGGER.debug(format("Unable to connect to %s - will try again later", host), e);
                    Thread.sleep(retryDelay);
                }
            }
        }
        DatabaseInstance defaultDatabaseInstance = databaseHotelAdminService.findDefaultDatabaseInstance();
        DatabaseHotelDataDao databaseHotelDataDao = defaultDatabaseInstance.getDatabaseHotelDataDao();
        ExternalSchemaManager externalSchemaManager = new ExternalSchemaManager(databaseHotelDataDao);
        databaseHotelAdminService.registerExternalSchemaManager(externalSchemaManager);
        LOGGER.info("Registered ExternalSchemaManager");
    }

    private void registerDatabase(Map<String, Object> databaseConfig) {

        String host = get(databaseConfig, "host");
        Optional<Boolean> createSchemaAllowed = maybeGetAsBoolean(databaseConfig, "createSchemaAllowed");
        Optional<Boolean> oracleScriptRequired = maybeGetAsBoolean(databaseConfig, "oracleScriptRequired");
        // The serviceLevel property has been renamed to instanceName, but we support both for a while.
        String instanceName =
            (String) maybeGet(databaseConfig, "instanceName").orElseGet(() -> get(databaseConfig, "serviceLevel"));
        databaseHotelAdminService.registerOracleDatabaseInstance(
            instanceName, host, get(databaseConfig, "service"), get(databaseConfig, "username"),
            get(databaseConfig, "password"), get(databaseConfig, "clientService"), createSchemaAllowed.orElse(true),
            oracleScriptRequired.orElse(false));
        LOGGER.info("Registered host [{}]", host);
    }
}
