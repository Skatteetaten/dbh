package no.skatteetaten.aurora.databasehotel.service;

import static java.lang.String.format;

import static no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.DEFAULT_SCHEMA_NAME;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.zaxxer.hikari.HikariDataSource;

import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager;
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDataSourceUtils;
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager;
import no.skatteetaten.aurora.databasehotel.dao.postres.PostgresDatabaseManager;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo;
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder;
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleResourceUsageCollector;
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder;
import no.skatteetaten.aurora.databasehotel.service.sits.ResidentsIntegration;

@Service
public class DatabaseHotelAdminService {

    private final Map<String, DatabaseInstance> databaseInstances = new HashMap<>();
    private final Long resourceUseCollectInterval;

    private DatabaseInstanceInitializer databaseInstanceInitializer;

    private int cooldownAfterDeleteMonths;
    private int cooldownDaysForOldUnusedSchemas;
    private String defaultInstanceName;
    private ExternalSchemaManager externalSchemaManager;

    public DatabaseHotelAdminService(DatabaseInstanceInitializer databaseInstanceInitializer,
        @Value("${database-config.cooldownMonths}") int cooldownAfterDeleteMonths,
        @Value("${database-config.cooldownDaysForOldUnusedSchemas:1}") int cooldownDaysForOldUnusedSchemas,
        @Value("${database-config.defaultInstanceName:}") String defaultInstanceName,
        @Value("${metrics.resourceUseCollectInterval}") Long resourceUseCollectInterval) {

        this.databaseInstanceInitializer = databaseInstanceInitializer;
        this.cooldownAfterDeleteMonths = cooldownAfterDeleteMonths;
        this.cooldownDaysForOldUnusedSchemas = cooldownDaysForOldUnusedSchemas;
        this.defaultInstanceName = defaultInstanceName;
        this.resourceUseCollectInterval = resourceUseCollectInterval;
    }

    public Set<DatabaseInstance> findAllDatabaseInstances() {

        return new HashSet<>(databaseInstances.values());
    }

    public void registerOracleDatabaseInstance(String instanceName, String dbHost, int port, String service,
        String username, String password, String clientService, boolean createSchemaAllowed,
        boolean oracleScriptRequired) {

        String managementJdbcUrl = new OracleJdbcUrlBuilder(service).create(dbHost, port, null);
        DatabaseInstanceMetaInfo databaseInstanceMetaInfo = new DatabaseInstanceMetaInfo(instanceName, dbHost, port);

        DataSource managementDataSource = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, username, password, oracleScriptRequired);

        DatabaseManager databaseManager = new OracleDatabaseManager(managementDataSource);

        databaseInstanceInitializer.assertInitialized(databaseManager, password);

        HikariDataSource databaseHotelDs = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, DEFAULT_SCHEMA_NAME, password, oracleScriptRequired);
        databaseInstanceInitializer.migrate(databaseHotelDs);

        DatabaseHotelDataDao databaseHotelDataDao = new OracleDatabaseHotelDataDao(databaseHotelDs);

        JdbcUrlBuilder jdbcUrlBuilder = new OracleJdbcUrlBuilder(clientService);

        OracleResourceUsageCollector resourceUsageCollector =
            new OracleResourceUsageCollector(managementDataSource, resourceUseCollectInterval);
        DatabaseInstance databaseInstance = new DatabaseInstance(databaseInstanceMetaInfo, databaseManager,
            databaseHotelDataDao, jdbcUrlBuilder, resourceUsageCollector, createSchemaAllowed,
            cooldownAfterDeleteMonths, cooldownDaysForOldUnusedSchemas);
        ResidentsIntegration residentsIntegration =
            new ResidentsIntegration(managementDataSource);
        databaseInstance.registerIntegration(residentsIntegration);

        registerDatabaseInstance(databaseInstance);
    }

    public void registerPostgresDatabaseInstance(String instanceName, String dbHost, int port, String username,
        String password, boolean createSchemaAllowed) {

        PostgresJdbcUrlBuilder urlBuilder = new PostgresJdbcUrlBuilder();
        String managementJdbcUrl = urlBuilder.create(dbHost, port, "postgres");
        DatabaseInstanceMetaInfo databaseInstanceMetaInfo = new DatabaseInstanceMetaInfo(instanceName, dbHost, port);
        DataSource managementDataSource = DataSourceUtils.createDataSource(managementJdbcUrl, username, password);
        DatabaseManager databaseManager = new PostgresDatabaseManager(managementDataSource);

        databaseInstanceInitializer.assertInitialized(databaseManager, password);

        String database = DEFAULT_SCHEMA_NAME.toLowerCase();
        String jdbcUrl = urlBuilder.create(dbHost, port, database);
        HikariDataSource databaseHotelDs = DataSourceUtils.createDataSource(jdbcUrl, database, password);
        databaseInstanceInitializer.migrate(databaseHotelDs);

        DatabaseHotelDataDao databaseHotelDataDao = new OracleDatabaseHotelDataDao(databaseHotelDs);

        ResourceUsageCollector resourceUsageCollector =
            new ResourceUsageCollector() {
                @Override
                public List<SchemaSize> getSchemaSizes() {
                    return Collections.emptyList();
                }

                @Override
                public Optional<SchemaSize> getSchemaSize(String schemaName) {
                    return Optional.of(new SchemaSize(schemaName, BigDecimal.ZERO));
                }
            };
        DatabaseInstance databaseInstance = new DatabaseInstance(databaseInstanceMetaInfo, databaseManager,
            databaseHotelDataDao, urlBuilder, resourceUsageCollector, createSchemaAllowed,
            cooldownAfterDeleteMonths, cooldownDaysForOldUnusedSchemas);

        registerDatabaseInstance(databaseInstance);
    }

    public void registerDatabaseInstance(DatabaseInstance databaseInstance) {

        databaseInstances.put(databaseInstance.getMetaInfo().getHost(), databaseInstance);
    }

    /**
     * Will try to find the DatabaseInstance with the instanceName <code>instanceNameOption</code>. If
     * <code>instanceNameOption</code> is null the method will return a DatabaseInstance if-and-only-if there is only
     * one DatabaseInstance registered. In all other cases it will throw a
     * <code>{@link DatabaseServiceException}</code>.
     *
     * @param instanceNameOption the nullable instanceName
     * @return
     */
    public DatabaseInstance findDatabaseInstanceOrFail(String instanceNameOption) {

        Set<DatabaseInstance> instances = findAllDatabaseInstances();
        if (instances.isEmpty()) {
            throw new DatabaseServiceException("No database instances registered");
        }
        String instanceName = Optional.ofNullable(instanceNameOption).orElseGet(() -> {
            List<DatabaseInstance> createSchemaInstances =
                instances.stream().filter(DatabaseInstance::isCreateSchemaAllowed).collect(Collectors.toList());

            if (createSchemaInstances.isEmpty()) {
                throw new DatabaseServiceException("Schema creation has been disabled for all instances");
            }

            int random = new Random().nextInt(createSchemaInstances.size());
            return createSchemaInstances.get(random).getInstanceName();
        });
        return findDatabaseInstanceByInstanceName(instanceName)
            .orElseThrow(() -> new DatabaseServiceException(format("No instance named [%s]", instanceName)));
    }

    public Optional<DatabaseInstance> findDatabaseInstanceByInstanceName(String instanceName) {

        Collection<DatabaseInstance> dbInstances = this.databaseInstances.values();
        for (DatabaseInstance databaseInstance : dbInstances) {
            if (databaseInstance.getInstanceName().equals(instanceName)) {
                return Optional.of(databaseInstance);
            }
        }
        return Optional.empty();
    }

    public Optional<DatabaseInstance> findDatabaseInstanceByHost(String host) {

        return Optional.ofNullable(databaseInstances.get(host));
    }

    public DatabaseInstance findDefaultDatabaseInstance() {

        if (databaseInstances.isEmpty()) {
            throw new DatabaseServiceException("No database instances registered");
        }
        // If there is only one database instance registered, we just return that one without further ado.
        if (databaseInstances.size() == 1) {
            return databaseInstances.entrySet().stream().findFirst().get().getValue();
        }
        if (Strings.isNullOrEmpty(defaultInstanceName)) {
            throw new DatabaseServiceException("More than one database instance registered but "
                + "database-config.defaultInstanceName has not been specified.");
        }
        return findDatabaseInstanceByInstanceName(defaultInstanceName)
            .orElseThrow(() -> new DatabaseServiceException(format("Unable to find database instance %s among "
                + "registered instances %s", defaultInstanceName, databaseInstances.keySet())));
    }

    public void registerExternalSchemaManager(ExternalSchemaManager externalSchemaManager) {
        this.externalSchemaManager = externalSchemaManager;
    }

    public Optional<ExternalSchemaManager> getExternalSchemaManager() {
        return Optional.ofNullable(externalSchemaManager);
    }
}
