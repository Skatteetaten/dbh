package no.skatteetaten.aurora.databasehotel.service

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsNone
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
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
import no.skatteetaten.aurora.databasehotel.cleanPostgresTestSchemas
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.deleteNonSystemSchemas
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat as jassertThat

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
    var createdSchemas = arrayListOf<Schema>()

    protected fun addSchema(s: DatabaseSchema): DatabaseSchema {
        createdSchemas.add(Schema(s.name))
        return s
    }

    protected fun addSchemas(s: List<DatabaseSchema>): List<DatabaseSchema> {
        s.forEach { addSchema(it) }
        return s
    }

    @Test
    fun `create schema and connect to it`() {

        val schema = addSchema(instance.createDefaultSchema())
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        JdbcTemplate(DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1))
    }

    @Test
    fun `verify schema privileges`() {

        val schema = addSchema(instance.createDefaultSchema())
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

        val schema = addSchema(instance.createDefaultSchema())
        assertThat(instance.findSchemaById(schema.id)).isNotNull()

        instance.deactivateSchema(schema.name, Duration.ofSeconds(1))
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
        ).forEach { addSchema(instance.createSchema(defaultLabels + it)) }
        repeat(3) { addSchema(instance.createSchema(defaultLabels)) }

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

        val (s1, s2, s3) = addSchemas(createSchemasWhereSomeHaveExpiredCooldowns())

        val schemasAfterExpiry = instance.findAllSchemasWithExpiredCooldowns()

        assertThat(schemasAfterExpiry.map { it.id }).containsAll(s1.id, s2.id)
        assertThat(schemasAfterExpiry.map { it.id }).containsNone(s3.id)
    }

    protected fun createSchemasWhereSomeHaveExpiredCooldowns(): List<DatabaseSchema> {
        val deleteAfter = Duration.ofSeconds(1)

//        assertThat(instance.findAllSchemas()).isEmpty()

        val s1 = instance.createDefaultSchema()
        val s2 = instance.createDefaultSchema()
        val s3 = instance.createDefaultSchema()

//        assertThat(instance.findAllSchemas()).hasSize(3)

        instance.deactivateSchema(s1.name, deleteAfter)
        instance.deactivateSchema(s2.name, deleteAfter)

//        assertThat(instance.findAllSchemas()).hasSize(1)
//        assertThat(instance.findAllSchemas(true)).hasSize(3)

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
    val databaseManager = PostgresDatabaseManager(dataSource)

    @BeforeEach
    fun setup() {
        instance = databaseInstanceInitializer.createInitializedPostgresInstance(config, instanceLabels = mapOf())
    }

    @AfterEach
    fun cleanup() {
        databaseManager.cleanPostgresTestSchemas(createdSchemas)
        createdSchemas = arrayListOf()
    }
    @Test
    fun `create schema when deletion is disabled fails`() {

        val instance = databaseInstanceInitializer.createInitializedPostgresInstance(
            config,
            createSchemaAllowed = false,
            instanceLabels = mapOf()
        )
        assertThat { instance.createSchema(emptyMap()) }
            .isFailure().hasClass(DatabaseServiceException::class)
    }

    @Test
    fun `permanently delete schemas with expired cooldowns`() {

        val (_, _, s3) = createSchemasWhereSomeHaveExpiredCooldowns()
        addSchema(s3)

        instance.deleteSchemasWithExpiredCooldowns()

        val schemasAfterDeletion = instance.findAllSchemas(true)
        assertThat(schemasAfterDeletion).hasSize(1)
        assertThat(schemasAfterDeletion.map { it.id }).containsAll(s3.id)
    }
}

@OracleTest
@DatabaseTest
class OracleDatabaseInstanceTest @Autowired constructor(
    val config: OracleConfig,
    @TargetEngine(ORACLE) val dataSource: DataSource,
    val databaseInstanceInitializer: DatabaseInstanceInitializer
) : AbstractDatabaseInstanceTest() {

    @BeforeEach
    fun setup() {
        OracleDatabaseManager(dataSource).deleteNonSystemSchemas()
        instance = databaseInstanceInitializer.createInitializedOracelInstance(config)
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

fun DatabaseInstanceInitializer.createInitializedPostgresInstance(
    config: PostgresConfig,
    instanceName: String = "dev",
    createSchemaAllowed: Boolean = true,
    instanceLabels: Map<String, String> = emptyMap()
) =
    this.createInitializedPostgresInstance(
        instanceName,
        config.host,
        config.port.toInt(),
        config.username,
        config.password,
        createSchemaAllowed,
        instanceLabels
    )

fun DatabaseInstanceInitializer.createInitializedOracelInstance(
    config: OracleConfig,
    instanceName: String = "dev"
) =
    this.createInitializedOracleInstance(
        instanceName,
        config.host,
        config.port.toInt(),
        config.service,
        config.username,
        config.password,
        config.clientService,
        true,
        config.oracleScriptRequired.toBoolean(),
        mapOf()
    )
