package no.skatteetaten.aurora.databasehotel.service

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.zaxxer.hikari.pool.HikariPool
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
import no.skatteetaten.aurora.databasehotel.deleteNonSystemSchemas
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat as jassertThat

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

        JdbcTemplate(DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1))
    }

    @Test
    fun `verify schema privileges`() {

        val schema = instance.createSchema(defaultLabels)
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        val jdbcTemplate = JdbcTemplate(DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1))
        listOf(
            "create table NAMES(name varchar(32))",
            "insert into NAMES (name) values ('Darth')",
            "CREATE VIEW nameview as select * from NAMES"
        ).forEach(jdbcTemplate::execute)
        assertThat(jdbcTemplate.queryForMap("select name from NAMES")["name"]).isEqualTo("Darth")
    }

    @Test
    fun `delete schema`() {

        val schema = instance.createSchema(defaultLabels)
        assertThat(instance.findSchemaById(schema.id)).isNotNull()

        instance.deleteSchema(schema.name, Duration.ofSeconds(1))
        assertThat(instance.findSchemaById(schema.id)).isNull()

        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")
        assertThat { DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1) }
            .thrownError { hasClass(HikariPool.PoolInitializationException::class) }
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

        assertThat(instance.findAllSchemas(emptyMap())).hasSize(8)

        jassertThat(instance.findAllSchemas(mapOf("environment" to "test")))
            .allMatch { it.labels["environment"] == "test" }
            .hasSize(3)

        jassertThat(instance.findAllSchemas(mapOf("affiliation" to "aurora", "environment" to "test")))
            .allMatch { it.labels["affiliation"] == "aurora" && it.labels["environment"] == "test" }
            .hasSize(2)
    }
}

@DatabaseTest
class PostgresDatabaseInstanceTest @Autowired constructor(
    val config: PostgresConfig,
    @TargetEngine(POSTGRES) val dataSource: DataSource,
    val databaseInstanceInitializer: DatabaseInstanceInitializer
) : AbstractDatabaseInstanceTest() {

    @BeforeEach
    fun setup() {
        PostgresDatabaseManager(dataSource).deleteNonSystemSchemas()
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
class OracleDatabaseInstanceTest @Autowired constructor(
    val testConfig: OracleConfig,
    @TargetEngine(ORACLE) val dataSource: DataSource,
    val databaseInstanceInitializer: DatabaseInstanceInitializer
) : AbstractDatabaseInstanceTest() {

    @BeforeEach
    fun setup() {
        OracleDatabaseManager(dataSource).deleteNonSystemSchemas()

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