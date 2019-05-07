package no.skatteetaten.aurora.databasehotel.service

import assertk.assertions.hasClass
import com.zaxxer.hikari.pool.HikariPool
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.TestConfig2
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Duration
import javax.sql.DataSource

abstract class AbstractDatabaseInstanceTest {

    lateinit var instance: DatabaseInstance

    @Test
    fun `create schema and connect to it`() {

        val schema = instance.createSchema(emptyMap())
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        val jdbcTemplate = JdbcTemplate(DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1))
        jdbcTemplate.execute("create table test(id integer not null);")
    }

    @Test
    fun `delete schema`() {

        val schema = instance.createSchema(emptyMap())
        assertThat(instance.findSchemaById(schema.id)).isPresent

        instance.deleteSchema(schema.name, Duration.ofSeconds(1))
        assertThat(instance.findSchemaById(schema.id)).isNotPresent

        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")
        // We should no longer be able to connect to the database schema
        assertk.assertThat { DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1) }
            .thrownError { hasClass(HikariPool.PoolInitializationException::class) }
    }

    @Test
    fun `find schemas`() {

        listOf(
            mapOf("affiliation" to "aurora", "env" to "dev", "app" to "ref"),
            mapOf("affiliation" to "aurora", "env" to "dev", "app" to "ref2"),
            mapOf("affiliation" to "aurora", "env" to "test", "app" to "ref"),
            mapOf("affiliation" to "aurora", "env" to "test", "app" to "ref2"),
            mapOf("affiliation" to "paas", "env" to "test", "app" to "ref")
        ).forEach { instance.createSchema(it) }
        repeat(3) { instance.createSchema(emptyMap()) }

        assertThat(instance.findAllSchemas(emptyMap()))
            .hasSize(8)

        assertThat(instance.findAllSchemas(mapOf("env" to "test")))
            .allMatch { it.labels["env"] == "test" }
            .hasSize(3)

        assertThat(instance.findAllSchemas(mapOf("affiliation" to "aurora", "env" to "test")))
            .allMatch { it.labels["affiliation"] == "aurora" && it.labels["env"] == "test" }
            .hasSize(2)
    }
}

@ExtendWith(SpringExtension::class)
@JsonTest
@ContextConfiguration(classes = [TestConfig2::class, DatabaseInstanceInitializer::class])
class PostgresDatabaseInstanceTest : AbstractDatabaseInstanceTest() {

    @Autowired
    lateinit var testConfig: TestConfig2

    @Autowired
    @TargetEngine(POSTGRES)
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var databaseInstanceInitializer: DatabaseInstanceInitializer

    @BeforeEach
    fun setup() {
        PostgresDatabaseManager(dataSource).apply { findAllNonSystemSchemas().forEach { deleteSchema(it.username) } }

        instance = databaseInstanceInitializer.createInitializedPostgresInstance(
            "dev",
            testConfig.host,
            testConfig.port.toInt(),
            testConfig.username,
            testConfig.password,
            true,
            mapOf()
        )
    }

    @Test
    fun `create schema when deletion is disabled fails`() {

        val instance = databaseInstanceInitializer.createInitializedPostgresInstance(
            "dev",
            testConfig.host,
            testConfig.port.toInt(),
            testConfig.username,
            testConfig.password,
            false,
            mapOf()
        )
        assertk.assertThat { instance.createSchema(emptyMap()) }.thrownError { hasClass(DatabaseServiceException::class) }
    }
}

@ExtendWith(SpringExtension::class)
@JsonTest
@ContextConfiguration(classes = [TestConfig2::class, DatabaseInstanceInitializer::class])
class OracleDatabaseInstanceTest : AbstractDatabaseInstanceTest() {

    @Autowired
    lateinit var testConfig: TestConfig2

    @Autowired
    @TargetEngine(POSTGRES)
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var databaseInstanceInitializer: DatabaseInstanceInitializer

    @BeforeEach
    fun setup() {
        PostgresDatabaseManager(dataSource).apply { findAllNonSystemSchemas().forEach { deleteSchema(it.username) } }

        instance = databaseInstanceInitializer.createInitializedPostgresInstance(
            "dev",
            testConfig.host,
            testConfig.port.toInt(),
            testConfig.username,
            testConfig.password,
            true,
            mapOf()
        )
    }
}