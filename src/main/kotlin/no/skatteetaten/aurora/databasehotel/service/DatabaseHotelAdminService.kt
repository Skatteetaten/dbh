package no.skatteetaten.aurora.databasehotel.service

import com.google.common.base.Strings
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.Companion.DEFAULT_SCHEMA_NAME
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleResourceUsageCollector
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import no.skatteetaten.aurora.databasehotel.service.sits.ResidentsIntegration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.lang.String.format
import java.math.BigDecimal
import java.util.HashMap
import java.util.HashSet
import java.util.Optional
import java.util.Random

@Service
class DatabaseHotelAdminService(
    private val databaseInstanceInitializer: DatabaseInstanceInitializer,
    @Value("\${database-config.cooldownMonths}") private val cooldownAfterDeleteMonths: Int,
    @Value("\${database-config.cooldownDaysForOldUnusedSchemas:1}") private val cooldownDaysForOldUnusedSchemas: Int,
    @Value("\${database-config.defaultInstanceName:}") private val defaultInstanceName: String,
    @Value("\${metrics.resourceUseCollectInterval}") private val resourceUseCollectInterval: Long?
) {

    private val databaseInstances: MutableMap<String, DatabaseInstance> = HashMap()
    private var externalSchemaManager: ExternalSchemaManager? = null

    fun findAllDatabaseInstances(): Set<DatabaseInstance> = HashSet(databaseInstances.values)

    fun registerOracleDatabaseInstance(
        instanceName: String, dbHost: String, port: Int, service: String,
        username: String, password: String, clientService: String, createSchemaAllowed: Boolean,
        oracleScriptRequired: Boolean
    ) {

        val managementJdbcUrl = OracleJdbcUrlBuilder(service).create(dbHost, port, null)
        val databaseInstanceMetaInfo = DatabaseInstanceMetaInfo(instanceName, dbHost, port, createSchemaAllowed)

        val managementDataSource = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, username, password, oracleScriptRequired
        )

        val databaseManager = OracleDatabaseManager(managementDataSource)

        databaseInstanceInitializer.assertInitialized(databaseManager, password)

        val databaseHotelDs = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, DEFAULT_SCHEMA_NAME, password, oracleScriptRequired
        )
        databaseInstanceInitializer.migrate(databaseHotelDs)

        val databaseHotelDataDao = OracleDatabaseHotelDataDao(databaseHotelDs)

        val jdbcUrlBuilder = OracleJdbcUrlBuilder(clientService)

        val resourceUsageCollector = OracleResourceUsageCollector(managementDataSource, resourceUseCollectInterval)
        val databaseInstance = DatabaseInstance(
            databaseInstanceMetaInfo, databaseManager,
            databaseHotelDataDao, jdbcUrlBuilder, resourceUsageCollector, createSchemaAllowed,
            cooldownAfterDeleteMonths, cooldownDaysForOldUnusedSchemas
        )
        val residentsIntegration = ResidentsIntegration(managementDataSource)
        databaseInstance.registerIntegration(residentsIntegration)

        registerDatabaseInstance(databaseInstance)
    }

    fun registerPostgresDatabaseInstance(
        instanceName: String, dbHost: String, port: Int, username: String,
        password: String, createSchemaAllowed: Boolean
    ) {

        val urlBuilder = PostgresJdbcUrlBuilder()
        val managementJdbcUrl = urlBuilder.create(dbHost, port, "postgres")
        val databaseInstanceMetaInfo = DatabaseInstanceMetaInfo(instanceName, dbHost, port, createSchemaAllowed)
        val managementDataSource = DataSourceUtils.createDataSource(managementJdbcUrl, username, password)
        val databaseManager = PostgresDatabaseManager(managementDataSource)

        databaseInstanceInitializer.assertInitialized(databaseManager, password)

        val database = DEFAULT_SCHEMA_NAME.toLowerCase()
        val jdbcUrl = urlBuilder.create(dbHost, port, database)
        val databaseHotelDs = DataSourceUtils.createDataSource(jdbcUrl, database, password)
        databaseInstanceInitializer.migrate(databaseHotelDs)

        val databaseHotelDataDao = PostgresDatabaseHotelDataDao(databaseHotelDs)

        val resourceUsageCollector = object : ResourceUsageCollector {
            override fun getSchemaSizes(): List<ResourceUsageCollector.SchemaSize> {
                return emptyList()
            }

            override fun getSchemaSize(schemaName: String): Optional<ResourceUsageCollector.SchemaSize> {
                return Optional.of(ResourceUsageCollector.SchemaSize(schemaName, BigDecimal.ZERO))
            }
        }
        val databaseInstance = DatabaseInstance(
            databaseInstanceMetaInfo, databaseManager,
            databaseHotelDataDao, urlBuilder, resourceUsageCollector, createSchemaAllowed,
            cooldownAfterDeleteMonths, cooldownDaysForOldUnusedSchemas
        )

        registerDatabaseInstance(databaseInstance)
    }

    fun registerDatabaseInstance(databaseInstance: DatabaseInstance) {

        databaseInstances[databaseInstance.metaInfo.host] = databaseInstance
    }

    /**
     * Will try to find the DatabaseInstance with the instanceName `instanceNameOption`. If
     * `instanceNameOption` is null the method will return a DatabaseInstance if-and-only-if there is only
     * one DatabaseInstance registered. In all other cases it will throw a
     * `[DatabaseServiceException]`.
     *
     * @param instanceNameOption the nullable instanceName
     * @return
     */
    fun findDatabaseInstanceOrFail(instanceNameOption: String?): DatabaseInstance {

        val instances = findAllDatabaseInstances()
        if (instances.isEmpty()) {
            throw DatabaseServiceException("No database instances registered")
        }
        val instanceName = Optional.ofNullable(instanceNameOption).orElseGet {
            val createSchemaInstances = instances.filter(DatabaseInstance::isCreateSchemaAllowed)

            if (createSchemaInstances.isEmpty()) {
                throw DatabaseServiceException("Schema creation has been disabled for all instances")
            }

            val random = Random().nextInt(createSchemaInstances.size)
            createSchemaInstances[random].instanceName
        }
        return findDatabaseInstanceByInstanceName(instanceName)
            .orElseThrow { DatabaseServiceException(format("No instance named [%s]", instanceName)) }
    }

    fun findDatabaseInstanceByInstanceName(instanceName: String): Optional<DatabaseInstance> {

        val dbInstances = this.databaseInstances.values
        for (databaseInstance in dbInstances) {
            if (databaseInstance.instanceName == instanceName) {
                return Optional.of(databaseInstance)
            }
        }
        return Optional.empty()
    }

    fun findDatabaseInstanceByHost(host: String): DatabaseInstance? = databaseInstances[host]

    fun findDefaultDatabaseInstance(): DatabaseInstance {

        if (databaseInstances.isEmpty()) {
            throw DatabaseServiceException("No database instances registered")
        }
        // If there is only one database instance registered, we just return that one without further ado.
        if (databaseInstances.size == 1) {
            return databaseInstances.entries.stream().findFirst().get().value
        }
        if (Strings.isNullOrEmpty(defaultInstanceName)) {
            throw DatabaseServiceException("More than one database instance registered but " + "database-config.defaultInstanceName has not been specified.")
        }
        return findDatabaseInstanceByInstanceName(defaultInstanceName)
            .orElseThrow {
                DatabaseServiceException(
                    format(
                        "Unable to find database instance %s among " + "registered instances %s",
                        defaultInstanceName,
                        databaseInstances.keys
                    )
                )
            }
    }

    fun registerExternalSchemaManager(externalSchemaManager: ExternalSchemaManager) {
        this.externalSchemaManager = externalSchemaManager
    }

    fun getExternalSchemaManager(): Optional<ExternalSchemaManager> {
        return Optional.ofNullable(externalSchemaManager)
    }
}
