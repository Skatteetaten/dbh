package no.skatteetaten.aurora.databasehotel.dao

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.service.createSchemaNameAndPassword
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

abstract class DatabaseHotelDataDaoTest {

    lateinit var hotelDataDao: DatabaseHotelDataDao

    @Test
    fun `finds created schema data`() {

        val schema = hotelDataDao.createSchemaData("TEST")
        assertThat(hotelDataDao.findSchemaDataById(schema.id).get().name).isEqualTo("TEST")
    }

    @Test
    fun `delete schema data`() {

        val schema = hotelDataDao.createSchemaData("TO_DELETE")
        assertThat(hotelDataDao.findSchemaDataById(schema.id).isPresent).isTrue()

        hotelDataDao.deactivateSchemaData(schema.id)
        assertThat(hotelDataDao.findSchemaDataById(schema.id).isPresent()).isFalse()
    }

    @Test
    fun `fails to create user for nonexisting schema`() {

        assertThat { hotelDataDao.createUser("NOSUCHSCHEMAID", "SCHEMA", "A", "A") }
            .thrownError { hasClass(DataAccessException::class) }
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

    @BeforeAll
    fun setup() {
        val manager = PostgresDatabaseManager(dataSource)
        val (username, password) = createSchemaNameAndPassword()
        val schemaName = manager.createSchema(username, password)
        val jdbcUrl = dataSource.jdbcUrl.replace(Regex("/[a-z]+$"), "/$schemaName")
        val dataSource = DataSourceUtils.createDataSource(jdbcUrl, schemaName, password)
        initializer.migrate(dataSource)

        hotelDataDao = PostgresDatabaseHotelDataDao(dataSource)
    }
}