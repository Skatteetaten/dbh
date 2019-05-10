package no.skatteetaten.aurora.databasehotel.service

import assertk.assertions.hasClass
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.OracleConfig
import no.skatteetaten.aurora.databasehotel.OracleTest
import no.skatteetaten.aurora.databasehotel.PostgresConfig
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import javax.sql.DataSource

abstract class AbstractDatabaseInstanceTest {

    lateinit var instance: DatabaseInstance

    private val defaultLabels = mapOf(
        "userId" to "id",
        "affiliation" to "aurora",
        "environment" to "dev",
        "application" to "ref",
        "name" to "db"
    )

    @Test
    fun `create schema and connect to it`() {

        val schema = instance.createSchema(defaultLabels)
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        val jdbcTemplate = JdbcTemplate(DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1))
        jdbcTemplate.execute("create table TEST(name varchar(32))")
    }

    @Test
    fun `delete schema`() {

        val schema = instance.createSchema(defaultLabels)
        assertThat(instance.findSchemaById(schema.id)).isPresent

        instance.deleteSchema(schema.name, Duration.ofSeconds(1))
        assertThat(instance.findSchemaById(schema.id)).isNotPresent

        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")
        DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1)
        // TODO: We should no longer be able to connect to the database schema
//        assertk.assertThat { DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1) }
//            .thrownError { hasClass(HikariPool.PoolInitializationException::class) }
    }

    @Test
    fun `find schemas`() {

        listOf(
            mapOf(),
            mapOf("application" to "ref2"),
            mapOf("environment" to "test"),
            mapOf("environment" to "test", "application" to "ref2"),
            mapOf("affiliation" to "paas", "environment" to "test")
        ).forEach { instance.createSchema(defaultLabels + it) }
        repeat(3) { instance.createSchema(defaultLabels) }

        assertThat(instance.findAllSchemas(emptyMap()))
            .hasSize(8)

        assertThat(instance.findAllSchemas(mapOf("environment" to "test")))
            .allMatch { it.labels["environment"] == "test" }
            .hasSize(3)

        assertThat(instance.findAllSchemas(mapOf("affiliation" to "aurora", "environment" to "test")))
            .allMatch { it.labels["affiliation"] == "aurora" && it.labels["environment"] == "test" }
            .hasSize(2)
    }
}

@DatabaseTest
class PostgresDatabaseInstanceTest : AbstractDatabaseInstanceTest() {

    @Autowired
    lateinit var config: PostgresConfig

    @Autowired
    @TargetEngine(POSTGRES)
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var databaseInstanceInitializer: DatabaseInstanceInitializer

    @BeforeEach
    fun setup() {
        PostgresDatabaseManager(dataSource).apply { findAllNonSystemSchemas().forEach { deleteSchema(it.username) } }
        instance = createInitializedPostgresInstance(true)
    }

    @Test
    fun `create schema when deletion is disabled fails`() {

        val instance = createInitializedPostgresInstance(false)
        assertk.assertThat { instance.createSchema(emptyMap()) }
            .thrownError { hasClass(DatabaseServiceException::class) }
    }

    private fun createInitializedPostgresInstance(createSchemaAllowed: Boolean) =
        databaseInstanceInitializer.createInitializedPostgresInstance(
            "dev",
            config.host,
            config.port.toInt(),
            config.username,
            config.password,
            createSchemaAllowed,
            mapOf()
        )
}

@OracleTest
@DatabaseTest
class OracleDatabaseInstanceTest : AbstractDatabaseInstanceTest() {

    @Autowired
    lateinit var testConfig: OracleConfig

    @Autowired
    @TargetEngine(ORACLE)
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var databaseInstanceInitializer: DatabaseInstanceInitializer

    @BeforeEach
    fun setup() {
        OracleDatabaseManager(dataSource).apply {
            findAllNonSystemSchemas()
                .filter { !listOf("MAPTEST", "AOS_API_USER", "RESIDENTS").contains(it.username) }
                .forEach { deleteSchema(it.username) }
        }

        instance = databaseInstanceInitializer.createInitializedOracleInstance(
            "dev",
            testConfig.host,
            testConfig.port.toInt(),
            testConfig.service,
            testConfig.username,
            testConfig.password,
            testConfig.clientService,
            true,
            testConfig.oracleScriptRequired.toBoolean(),
            mapOf()
        )
    }
}