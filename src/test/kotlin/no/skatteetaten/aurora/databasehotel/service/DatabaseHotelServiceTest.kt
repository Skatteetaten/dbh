package no.skatteetaten.aurora.databasehotel.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import javax.sql.DataSource
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.PostgresCleaner
import no.skatteetaten.aurora.databasehotel.PostgresConfig
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@DatabaseTest
class DatabaseHotelServiceTest @Autowired constructor(
    val config: PostgresConfig,
    @TargetEngine(POSTGRES) val dataSource: DataSource
) {
    val instanceNames = listOf("dev1" to "aurora", "dev2" to "part", "dev3" to "memo")

    val databaseInstanceInitializer = DatabaseInstanceInitializer()
    val adminService = DatabaseHotelAdminService(databaseInstanceInitializer)
    val databaseHotelService = DatabaseHotelService(adminService)
    val databaseManager = PostgresDatabaseManager(dataSource)

    val postgresCleaner = PostgresCleaner(dataSource)

    @BeforeEach
    fun setup() {
        // Simulate a few DatabaseInstances by creating databases on the test server, initializing the management
        // schemas in these databases and giving the roles superuser privileges.
        instanceNames.map { (instanceName, affiliation) ->
            val (username, password) = createSchemaNameAndPassword()
            val schemaName = databaseManager.createSchema(username, password)
            databaseInstanceInitializer.schemaName = schemaName
            val conf = config.copy(username = schemaName, password = password)
            val instance = databaseInstanceInitializer.createInitializedPostgresInstance(
                conf,
                instanceName = instanceName,
                instanceLabels = mapOf("affiliation" to affiliation)
            )
            JdbcTemplate(dataSource).update("alter user $schemaName with createrole createdb")
            adminService.registerDatabaseInstance(instance)
        }
    }

    @AfterEach
    fun cleanup() {
        postgresCleaner.cleanInstanceSchemas(adminService.findAllDatabaseInstances(POSTGRES))
        adminService.removeAllInstances()
    }

    @Test
    fun `create schema with instance labels`() {

        val schema1 = databaseHotelService.createSchema(
            DatabaseInstanceRequirements(
                databaseEngine = POSTGRES,
                instanceLabels = mapOf("affiliation" to "aurora")
            )
        )
        assertThat(schema1.databaseInstanceMetaInfo.instanceName).isEqualTo("dev1")

        val schema2 = databaseHotelService.createSchema(
            DatabaseInstanceRequirements(
                databaseEngine = POSTGRES,
                instanceLabels = mapOf("affiliation" to "memo")
            )
        )
        assertThat(schema2.databaseInstanceMetaInfo.instanceName).isEqualTo("dev3")
    }

    @Test
    fun `deactivate schema`() {

        val schemas = listOf("aurora" to 3, "memo" to 2, "part" to 1).flatMap { (affiliation, schemaCount) ->
            (1 to schemaCount).toList().map {
                databaseHotelService.createSchema(
                    DatabaseInstanceRequirements(
                        databaseEngine = POSTGRES,
                        instanceLabels = mapOf("affiliation" to affiliation)
                    )
                )
            }
        }

        val expected = listOf(0, 3)
            .map { schemas[it].id.also { databaseHotelService.deactivateSchema(it, null) } }.toSet()

        val actual = databaseHotelService.findAllInactiveDatabaseSchemas().map { it.id }.toSet()

        assertThat(actual).isEqualTo(expected)
    }
}
