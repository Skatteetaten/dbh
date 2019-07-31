package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.HashMap
import java.util.Random

@Service
class DatabaseHotelAdminService(
    private val databaseInstanceInitializer: DatabaseInstanceInitializer,
    @Value("\${database-config.defaultInstanceName:}") private val defaultInstanceName: String
) {

    var externalSchemaManager: ExternalSchemaManager? = null
    private val databaseInstances: MutableMap<String, DatabaseInstance> = HashMap()

    fun findAllDatabaseInstances(databaseEngine: DatabaseEngine? = null): Set<DatabaseInstance> =
        databaseInstances.values
            .filter { databaseEngine == null || it.metaInfo.engine == databaseEngine }
            .toSet()

    fun registerOracleDatabaseInstance(
        instanceName: String,
        dbHost: String,
        port: Int,
        service: String,
        username: String,
        password: String,
        clientService: String,
        createSchemaAllowed: Boolean,
        oracleScriptRequired: Boolean,
        instanceLabels: Map<String, String>
    ): DatabaseInstance {

        val databaseInstance = databaseInstanceInitializer.createInitializedOracleInstance(
            instanceName,
            dbHost,
            port,
            service,
            username,
            password,
            clientService,
            createSchemaAllowed,
            oracleScriptRequired,
            instanceLabels
        )
        return registerDatabaseInstance(databaseInstance)
    }

    fun registerPostgresDatabaseInstance(
        instanceName: String,
        dbHost: String,
        port: Int,
        username: String,
        password: String,
        createSchemaAllowed: Boolean,
        instanceLabels: Map<String, String>
    ): DatabaseInstance {

        val databaseInstance = databaseInstanceInitializer.createInitializedPostgresInstance(
            instanceName,
            dbHost,
            port,
            username,
            password,
            createSchemaAllowed,
            instanceLabels
        )
        return registerDatabaseInstance(databaseInstance)
    }

    fun registerDatabaseInstance(databaseInstance: DatabaseInstance): DatabaseInstance {

        databaseInstances[databaseInstance.metaInfo.host] = databaseInstance
        return databaseInstance
    }

    @JvmOverloads
    fun findDatabaseInstanceOrFail(requirements: DatabaseInstanceRequirements = DatabaseInstanceRequirements()): DatabaseInstance {

        val availableInstances = findAllDatabaseInstances()
            .filter { it.isCreateSchemaAllowed && it.metaInfo.engine == requirements.databaseEngine }
            .takeIf { it.isNotEmpty() }
            ?: throw DatabaseServiceException("Schema creation has been disabled for all instances with the required engine=${requirements.databaseEngine}")

        val openInstances = availableInstances.filter { it.metaInfo.labels.isEmpty() }

        val matchedLabelsInstances = availableInstances.filter { instance ->
            instance.metaInfo.labels.isNotEmpty() && instance.metaInfo.labels.all {
                it.value == requirements.instanceLabels[it.key]
            }
        }

        val matchedLabelInstanceName = getRandomInstanceName(matchedLabelsInstances)
        val fallbackInstanceName = if (requirements.instanceFallback) getRandomInstanceName(openInstances) else null

        val instanceName = requirements.instanceName
            ?: matchedLabelInstanceName
            ?: fallbackInstanceName
            ?: throw DatabaseServiceException("No matching instances found for engine=${requirements.databaseEngine} labels=${requirements.instanceLabels} instanceFallback=${requirements.instanceFallback}")

        return findDatabaseInstanceByInstanceName(instanceName)
            ?: throw DatabaseServiceException("No available instance named [%s] with the required engine $instanceName")
    }

    fun findDatabaseInstanceByInstanceName(instanceName: String): DatabaseInstance? {

        val dbInstances = this.databaseInstances.values
        for (databaseInstance in dbInstances) {
            if (databaseInstance.instanceName == instanceName) {
                return databaseInstance
            }
        }
        return null
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
        if (defaultInstanceName.isEmpty()) {
            throw DatabaseServiceException("More than one database instance registered but database-config.defaultInstanceName has not been specified.")
        }
        return findDatabaseInstanceByInstanceName(defaultInstanceName)
            ?: throw DatabaseServiceException("Unable to find database instance $defaultInstanceName among registered instances ${databaseInstances.keys}")
    }

    private fun getRandomInstanceName(instances: List<DatabaseInstance>): String? {
        if (instances.isEmpty()) return null

        val random = Random().nextInt(instances.size)
        return instances[random].instanceName
    }

    fun removeAllInstances() {
        databaseInstances.clear()
    }
}
