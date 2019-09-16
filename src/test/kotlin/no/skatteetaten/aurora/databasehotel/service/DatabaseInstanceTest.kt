package no.skatteetaten.aurora.databasehotel.service

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsNone
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.zaxxer.hikari.pool.HikariPool
import java.time.Duration
import javax.sql.DataSource
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
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import org.assertj.core.api.Assertions.assertThat as jassertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

private val defaultLabels = mapOf(
        "userId" to "id",
        "affiliation" to "aurora",
        "environment" to "dev",
        "application" to "ref",
        "name" to "db"
)

private fun DatabaseInstance.createDefaultSchema() = this.createSchema(defaultLabels)

abstract class AbstractDatabaseInstanceTest {

    lateinit var instance: DatabaseInstance

    @Test
    fun `create schema and connect to it`() {

        val schema = instance.createDefaultSchema()
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        JdbcTemplate(DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1))
    }

    @Test
    fun `verify schema privileges`() {

        val schema = instance.createDefaultSchema()
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

        val schema = instance.createDefaultSchema()
        assertThat(instance.findSchemaById(schema.id)).isNotNull()

        instance.deleteSchemaByCooldown(schema.name, Duration.ofSeconds(1))
        assertThat(instance.findSchemaById(schema.id)).isNull()

        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")
        assertThat { DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1) }
            .isFailure().hasClass(HikariPool.PoolInitializationException::class)
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

    @Test
    fun `find all schemas with expired cooldowns`() {

        val (s1, s2, s3) = createSchemasWhereSomeHaveExpiredCooldowns()

        val schemasAfterExpiry = instance.findAllSchemasWithExpiredCooldowns()

        assertThat(schemasAfterExpiry.map { it.id }).containsAll(s1.id, s2.id)
        assertThat(schemasAfterExpiry.map { it.id }).containsNone(s3.id)
    }

    protected fun createSchemasWhereSomeHaveExpiredCooldowns(): List<DatabaseSchema> {
        val deleteAfter = Duration.ofSeconds(1)

        assertThat(instance.findAllSchemas()).isEmpty()

        val s1 = instance.createDefaultSchema()
        val s2 = instance.createDefaultSchema()
        val s3 = instance.createDefaultSchema()

        assertThat(instance.findAllSchemas()).hasSize(3)

        instance.deleteSchemaByCooldown(s1.name, deleteAfter)
        instance.deleteSchemaByCooldown(s2.name, deleteAfter)

        assertThat(instance.findAllSchemas()).hasSize(1)
        assertThat(instance.findAllSchemas(true)).hasSize(3)

        Thread.sleep(deleteAfter.toMillis())
        return listOf(s1, s2, s3)
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
        assertThat { instance.createSchema(emptyMap()) }
            .isFailure().hasClass(DatabaseServiceException::class)
    }

    @Test
    fun `permanently delete schemas with expired cooldowns`() {

        val (_, _, s3) = createSchemasWhereSomeHaveExpiredCooldowns()

        instance.deleteSchemasWithExpiredCooldowns()

        val schemasAfterDeletion = instance.findAllSchemas(true)
        assertThat(schemasAfterDeletion).hasSize(1)
        assertThat(schemasAfterDeletion.map { it.id }).containsAll(s3.id)
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

    @Test
    fun `permanently delete schemas with expired cooldowns`() {

        val (s1, s2, s3) = createSchemasWhereSomeHaveExpiredCooldowns()

        instance.deleteSchemasWithExpiredCooldowns()

        val schemasAfterDeletion = instance.findAllSchemas(true)
        assertThat(schemasAfterDeletion).hasSize(1)
        assertThat(schemasAfterDeletion.map { it.id }).containsAll(s3.id)

        // Make sure that the schemas are not physically deleted. They will be deleted by a separate integration.
        val schemas = instance.databaseManager.findAllNonSystemSchemas()
        assertThat(schemas.map { it.username }).containsAll(s1.name, s2.name, s3.name)
    }
}
