package no.skatteetaten.aurora.databasehotel.dao

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.support.fail
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import java.util.Date
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.OracleTest
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.cleanPostgresTestSchema
import no.skatteetaten.aurora.databasehotel.createOracleSchema
import no.skatteetaten.aurora.databasehotel.createPostgresSchema
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

abstract class DatabaseHotelDataDaoTest {

    lateinit var hotelDataDao: DatabaseHotelDataDao

    @Test
    fun `finds created schema data`() {

        val schema = hotelDataDao.createSchemaData("TEST")
        assertThat(hotelDataDao.findSchemaDataById(schema.id)?.name).isEqualTo("TEST")
    }

    @Test
    fun `delete schema data`() {

        val schema = hotelDataDao.createSchemaData("TO_DELETE")
        assertThat(hotelDataDao.findSchemaDataById(schema.id)).isNotNull()

        val cooldownDuration = Duration.ofDays(7)

        hotelDataDao.deactivateSchemaData(schema.id, cooldownDuration)
        assertThat(hotelDataDao.findSchemaDataById(schema.id)).isNull()

        val schemaData = hotelDataDao.findSchemaDataById(schema.id, false)
        assertThat(schemaData).isNotNull()

        val now = Date()
        val deleteAfter = Date.from(now.toInstant().plus(cooldownDuration))
        assertThat(schemaData!!::active).isFalse()
        assertThat(schemaData::setToCooldownAt).isAboutEqualTo(now)
        assertThat(schemaData::deleteAfter).isAboutEqualTo(deleteAfter)
    }

    @Test
    fun `fails to create user for nonexisting schema`() {

        assertThat { hotelDataDao.createUser("NOSUCHSCHEMAID", "SCHEMA", "A", "A") }
            .isFailure().hasClass(DataAccessException::class)
    }

    @Test
    fun `create user`() {

        val schemaData = hotelDataDao.createSchemaData("SCHEMA_NAME")
        val user = hotelDataDao.createUser(schemaData.id, "SCHEMA", "SCHEMA_NAME", "PASS")

        assertThat(user.id).isNotNull()
        assertThat(user.schemaId).isEqualTo(schemaData.id)
        assertThat(user.type).isEqualTo("SCHEMA")
        assertThat(user.username).isEqualTo("SCHEMA_NAME")
        assertThat(user.password).isEqualTo("PASS")
    }

    @Test
    fun `find users for schema`() {

        val schemaData1 = hotelDataDao.createSchemaData("SCHEMA_NAME_1")
        val schemaData2 = hotelDataDao.createSchemaData("SCHEMA_NAME_2")
        val user1 = hotelDataDao.createUser(schemaData1.id, "SCHEMA", "SCHEMA_NAME_1", "PASS")
        val user2 = hotelDataDao.createUser(schemaData1.id, "READWRITE", "SCHEMA_NAME_1_rw", "PASS")
        hotelDataDao.createUser(schemaData2.id, "SCHEMA", "SCHEMA_NAME_2", "PASS")

        val users = hotelDataDao.findAllUsersForSchema(schemaData1.id)
        assertThat(users).hasSize(2)
        assertThat(users).containsAll(user1, user2)
    }

    @Test
    fun `replace and find all labels`() {

        val schemaData = hotelDataDao.createSchemaData("SCHEMA_NAME_1")

        hotelDataDao.replaceLabels(schemaData.id, mapOf("deploymentId" to "Test", "otherLabel" to "SomeValue"))

        val labels = hotelDataDao.findAllLabels()
        assertThat(labels).hasSize(2)
        assertThat(labels.map { it.schemaId }.toSet()).isEqualTo(setOf(schemaData.id))
        assertThat(labels.find { it.name == "deploymentId" }?.value).isEqualTo("Test")
        assertThat(labels.find { it.name == "otherLabel" }?.value).isEqualTo("SomeValue")
    }
}

@DatabaseTest
class PostgresDatabaseHotelDataDaoTest @Autowired constructor(
    @TargetEngine(POSTGRES) val dataSource: HikariDataSource,
    val initializer: DatabaseInstanceInitializer
) : DatabaseHotelDataDaoTest() {

    lateinit var createdSchemaName: String

    @BeforeAll
    fun setup() {
        val dataSource = createPostgresSchema(dataSource)
        createdSchemaName = dataSource.username
        initializer.migrate(dataSource)
        hotelDataDao = PostgresDatabaseHotelDataDao(dataSource)
    }

    @AfterAll
    fun cleanup() {
        PostgresDatabaseManager(dataSource).cleanPostgresTestSchema(createdSchemaName)
    }
}

@DatabaseTest
@OracleTest
class OracleDatabaseHotelDataDaoTest @Autowired constructor(
    @TargetEngine(ORACLE) val dataSource: HikariDataSource,
    val initializer: DatabaseInstanceInitializer
) : DatabaseHotelDataDaoTest() {

    @BeforeAll
    fun setup() {
        val dataSource = createOracleSchema(dataSource)
        initializer.migrate(dataSource)

        hotelDataDao = OracleDatabaseHotelDataDao(dataSource)
    }
}

fun Assert<Date?>.isAboutEqualTo(expected: Date) = given { actual ->
    val actualInstant = actual!!.toInstant()
    val expectedInstant = expected.toInstant()
    if (expectedInstant.plusSeconds(1).isAfter(actualInstant) && expectedInstant.plusSeconds(-1).isBefore(actualInstant)) return
    fail(expected, actual)
}
