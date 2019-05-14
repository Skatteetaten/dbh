package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.Companion.DEFAULT_SCHEMA_NAME
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.SingleColumnRowMapper
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@JsonTest
class DatabaseHotelAdminServiceTest {

    @Value("\${test.datasource.host}")
    lateinit var testHost: String

    @Value("\${test.datasource.port}")
    lateinit var testPort: String

    @Value("\${test.datasource.password}")
    lateinit var testPassword: String

    @MockBean
    lateinit var adminService: DatabaseHotelAdminService

    @Test
    fun `postgres smoke test`() {

        val defaultInstanceName = "postgres"
        val databaseInstanceInitializer = DatabaseInstanceInitializer(DEFAULT_SCHEMA_NAME.toLowerCase())
        val databaseHotelAdminService = DatabaseHotelAdminService(
            databaseInstanceInitializer, 6, 1,
            defaultInstanceName, 10000
        )

        val instance = databaseHotelAdminService.registerPostgresDatabaseInstance(
            defaultInstanceName,
            testHost,
            testPort.toInt(),
            "postgres",
            testPassword,
            true,
            mapOf()
        )

        val schema = instance.createSchema(emptyMap())
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        val dataSource = DataSourceUtils.createDataSource(schema.jdbcUrl, user.name, user.password, 1)

        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("create table test(id integer not null);")
    }

    @BeforeAll
    fun clean() {

        deleteTestDatabases(PostgresJdbcUrlBuilder().create(testHost, testPort.toInt(), "postgres"), "postgres", testPassword)
    }

    internal fun deleteTestDatabases(jdbcUrl: String, username: String, password: String) {
        val jdbcTemplate = JdbcTemplate(DataSourceUtils.createDataSource(jdbcUrl, username, password))
        jdbcTemplate.queryForStrings("SELECT datname FROM pg_database WHERE datistemplate = false and datname not in ('postgres')")
            .forEach {
                jdbcTemplate.update("drop database $it")
                jdbcTemplate.update("drop role $it")
            }
    }

    private fun JdbcTemplate.queryForStrings(query: String): List<String> =
        this.query(query, SingleColumnRowMapper(String::class.java))
}
