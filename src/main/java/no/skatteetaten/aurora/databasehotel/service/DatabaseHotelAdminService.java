package no.skatteetaten.aurora.databasehotel.service;

import static java.lang.String.format;

import static no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseInstanceInitializer.DEFAULT_SCHEMA_NAME;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager;
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager;
import no.skatteetaten.aurora.databasehotel.service.sits.ResidentsIntegration;
import no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseInstanceInitializer;
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDataSourceUtils;
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo;
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder;
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleResourceUsageCollector;

@Service
public class DatabaseHotelAdminService {

    public static final int PORT = 1521;

    private final Map<String, DatabaseInstance> databaseInstances = new HashMap<>();

    private DatabaseInstanceInitializer databaseInstanceInitializer;

    private int cooldownAfterDeleteMonths;
    private String defaultInstanceName;
    private ExternalSchemaManager externalSchemaManager;

    public DatabaseHotelAdminService(DatabaseInstanceInitializer databaseInstanceInitializer,
        @Value("${databaseConfig.cooldownMonths}") int cooldownAfterDeleteMonths,
        @Value("${databaseConfig.defaultInstanceName:}") String defaultInstanceName) {

        this.databaseInstanceInitializer = databaseInstanceInitializer;
        this.cooldownAfterDeleteMonths = cooldownAfterDeleteMonths;
        this.defaultInstanceName = defaultInstanceName;
    }

    public Set<DatabaseInstance> findAllDatabaseInstances() {

        return new HashSet<>(databaseInstances.values());
    }

    public void registerOracleDatabaseInstance(String instanceName, String dbHost, String service, String username,
        String password, String clientService, boolean oracleScriptRequired) {

        registerOracleDatabaseInstance(instanceName, dbHost, PORT, service, username, password,
            clientService, oracleScriptRequired);
    }

    public void registerOracleDatabaseInstance(String instanceName, String dbHost, int port, String service,
        String username, String password, String clientService, boolean oracleScriptRequired) {

        String managementJdbcUrl = new OracleJdbcUrlBuilder(service).create(dbHost, port);
        DatabaseInstanceMetaInfo databaseInstanceMetaInfo = new DatabaseInstanceMetaInfo(instanceName, dbHost, port);

        DataSource managementDataSource = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, username, password, oracleScriptRequired);

        DatabaseManager databaseManager = new OracleDatabaseManager(managementDataSource);

        databaseInstanceInitializer.assertInitialized(databaseManager, password);

        DataSource databaseHotelDs = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, DEFAULT_SCHEMA_NAME, password, oracleScriptRequired);
        databaseInstanceInitializer.migrate(databaseHotelDs);

        DatabaseHotelDataDao databaseHotelDataDao = new OracleDatabaseHotelDataDao(databaseHotelDs);

        JdbcUrlBuilder jdbcUrlBuilder = new OracleJdbcUrlBuilder(clientService);

        OracleResourceUsageCollector resourceUsageCollector = new OracleResourceUsageCollector(managementDataSource);
        DatabaseInstance databaseInstance = new DatabaseInstance(databaseInstanceMetaInfo, databaseManager,
            databaseHotelDataDao, jdbcUrlBuilder, resourceUsageCollector);
        ResidentsIntegration residentsIntegration =
            new ResidentsIntegration(managementDataSource, cooldownAfterDeleteMonths);
        databaseInstance.registerIntegration(residentsIntegration);

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

        Set<String> instanceNames = findAllInstanceNames();
        if (instanceNames.isEmpty()) {
            throw new DatabaseServiceException("No database instances registered");
        }
        String instanceName = Optional.ofNullable(instanceNameOption).orElseGet(() -> {
            if (instanceNames.size() != 1) {
                throw new DatabaseServiceException(format("No instance name specified and more than one registered "
                    + "(%s). Please specify desired instance", StringUtils.join(instanceNames, ",")));
            }
            return instanceNames.iterator().next();
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
                + "databaseConfig.defaultInstanceName has not been specified.");
        }
        return Optional.ofNullable(databaseInstances.get(defaultInstanceName))
            .orElseThrow(() -> new DatabaseServiceException(format("Unable to find database instance %s among "
                + "registered instances %s", defaultInstanceName, databaseInstances.keySet())));
    }

    public Set<String> findAllInstanceNames() {

        Set<DatabaseInstance> dbInstances = findAllDatabaseInstances();
        return dbInstances.stream().map(DatabaseInstance::getInstanceName).collect(Collectors.toSet());
    }

    public void registerExternalSchemaManager(ExternalSchemaManager externalSchemaManager) {
        this.externalSchemaManager = externalSchemaManager;
    }

    public Optional<ExternalSchemaManager> getExternalSchemaManager() {
        return Optional.ofNullable(externalSchemaManager);
    }
}
