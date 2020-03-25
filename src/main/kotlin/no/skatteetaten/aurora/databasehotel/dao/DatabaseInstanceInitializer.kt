package no.skatteetaten.aurora.databasehotel.dao

import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance
import no.skatteetaten.aurora.databasehotel.service.ResourceUsageCollector
import no.skatteetaten.aurora.databasehotel.service.SchemaSize
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleResourceUsageCollector
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import no.skatteetaten.aurora.databasehotel.service.sits.ResidentsIntegration
import no.skatteetaten.aurora.databasehotel.toDatabaseEngineFromJdbcUrl
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.sql.ResultSet

@Component
class DatabaseInstanceInitializer(
    @Value("\${database-config.cooldownDaysAfterDelete:30}") private val cooldownDaysAfterDelete: Int = 30,
    @Value("\${metrics.resourceUseCollectInterval}") private val resourceUseCollectInterval: Long = 300000L
) {

    var schemaName: String = DEFAULT_SCHEMA_NAME

    fun createInitializedOracleInstance(
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
        val managementJdbcUrl = OracleJdbcUrlBuilder(service).create(dbHost, port, null)
        val databaseInstanceMetaInfo =
            DatabaseInstanceMetaInfo(ORACLE, instanceName, dbHost, port, createSchemaAllowed, instanceLabels)

        val managementDataSource = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, username, password, oracleScriptRequired
        )

        val databaseManager = OracleDatabaseManager(managementDataSource)

        assertInitialized(databaseManager, password)

        val databaseHotelDs = OracleDataSourceUtils.createDataSource(
            managementJdbcUrl, schemaName, password, oracleScriptRequired
        )
        migrate(databaseHotelDs)

        (fun() {
            // migrate createdDate from dba_users to $schemaName.SCHEMA_DATA needs system privileges
            val jdbcTemplate = JdbcTemplate(managementDataSource)
            try {
                val hasRecord =
                    jdbcTemplate.query<Boolean>(
                        "select \"version\" from $schemaName.SCHEMA_VERSION where \"version\" = '202019031345'"
                    ) { rs: ResultSet ->
                        if (rs.next()) {
                            return@query true
                        }
                        false
                    }
                if (hasRecord == true) {
                    try {
                        logger.info("Running first batch")
                        val firstUpdates =
                            jdbcTemplate.update("UPDATE $schemaName.SCHEMA_DATA SD SET CREATED_DATE = (SELECT CREATED FROM DBA_USERS U WHERE SD.NAME = U.USERNAME) WHERE SD.CREATED_DATE IS NULL")
                        if (firstUpdates > 0) {
                            logger.info(
                                "Updated $schemaName.SCHEMA_DATA.CREATED_DATE with data from DBA_USERS, {} rows affected",
                                firstUpdates
                            )
                        }
                    } catch (e: Exception) {
                        logger.warn("Unable to update $schemaName.SCHEMA_DATA in first batch; {}", e.message)
                    }

                    try {
                        logger.info("Running second batch")
                        val secondUpdates =
                            jdbcTemplate.update("UPDATE $schemaName.SCHEMA_DATA SD SET CREATED_DATE = TO_TIMESTAMP('01-JAN-1990 12:00:00:00','DD-MON-YYYY HH24:MI:SS:FF') WHERE SD.CREATED_DATE IS NULL")
                        if (secondUpdates > 0) {
                            logger.info(
                                "Updated $schemaName.SCHEMA_DATA.CREATED_DATE to fictional date where created_date = null, {} rows affected",
                                secondUpdates
                            )
                        }
                    } catch (e: Exception) {
                        logger.warn("Unable to update $schemaName.SCHEMA_DATA in second batch; {}", e.message)
                    }
                }
            } catch (e: Exception) {
                logger.warn("Unable to select version from $schemaName.SCHEMA_DATA; {}", e.message)
            }
        })()

        val databaseHotelDataDao = OracleDatabaseHotelDataDao(databaseHotelDs)

        val jdbcUrlBuilder = OracleJdbcUrlBuilder(clientService)

        val resourceUsageCollector = OracleResourceUsageCollector(managementDataSource, resourceUseCollectInterval)
        val databaseInstance = DatabaseInstance(
            databaseInstanceMetaInfo, databaseManager,
            databaseHotelDataDao, jdbcUrlBuilder, resourceUsageCollector,
            cooldownDaysAfterDelete
        )
        val residentsIntegration = ResidentsIntegration(managementDataSource)
        databaseInstance.registerIntegration(residentsIntegration)

        return databaseInstance
    }

    fun createInitializedPostgresInstance(
        instanceName: String,
        dbHost: String,
        port: Int,
        username: String,
        password: String,
        createSchemaAllowed: Boolean,
        instanceLabels: Map<String, String>
    ): DatabaseInstance {

        val urlBuilder = PostgresJdbcUrlBuilder()
        val managementJdbcUrl = urlBuilder.create(dbHost, port, "postgres")
        val databaseInstanceMetaInfo =
            DatabaseInstanceMetaInfo(POSTGRES, instanceName, dbHost, port, createSchemaAllowed, instanceLabels)
        val managementDataSource = DataSourceUtils.createDataSource(managementJdbcUrl, username, password)
        val databaseManager = PostgresDatabaseManager(managementDataSource)

        assertInitialized(databaseManager, password)

        val database = schemaName.toLowerCase()
        val jdbcUrl = urlBuilder.create(dbHost, port, database)
        val databaseHotelDs = DataSourceUtils.createDataSource(jdbcUrl, database, password)

        migrate(databaseHotelDs)

        val databaseHotelDataDao = PostgresDatabaseHotelDataDao(databaseHotelDs)

        val resourceUsageCollector = object : ResourceUsageCollector {
            override val schemaSizes: List<SchemaSize>
                get() = emptyList()

            override fun getSchemaSize(schemaName: String): SchemaSize? {
                return SchemaSize(schemaName, BigDecimal.ZERO)
            }
        }
        return DatabaseInstance(
            databaseInstanceMetaInfo, databaseManager,
            databaseHotelDataDao, urlBuilder, resourceUsageCollector,
            cooldownDaysAfterDelete
        )
    }

    private fun assertInitialized(databaseManager: DatabaseManager, password: String) {

        if (!databaseManager.schemaExists(schemaName)) {
            databaseManager.createSchema(schemaName, password)
        }
        databaseManager.updatePassword(schemaName, password)
    }

    fun migrate(dataSource: HikariDataSource) {

        val engine = dataSource.jdbcUrl.toDatabaseEngineFromJdbcUrl()

        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .outOfOrder(true)
            .locations(engine.migrationLocation)

        if (engine == ORACLE) {
            configuration
                .schemas(dataSource.username)
                .table("SCHEMA_VERSION")
        }
        val flyway = configuration.load()

        flyway.migrate()
    }

    private val DatabaseEngine.migrationLocation
        get(): String = when (this) {
            POSTGRES -> "db/migration-postgres"
            ORACLE -> "db/migration"
        }

    companion object {

        val logger: Logger = LoggerFactory.getLogger(DatabaseInstanceInitializer::class.java)
        const val DEFAULT_SCHEMA_NAME: String = "DATABASEHOTEL_INSTANCE_DATA"
    }
}
