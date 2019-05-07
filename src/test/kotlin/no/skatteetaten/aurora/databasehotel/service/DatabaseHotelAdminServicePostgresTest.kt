package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.Companion.DEFAULT_SCHEMA_NAME
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@JsonTest
class DatabaseHotelAdminServicePostgresTest {

    @Value("\${test.datasource.postgres.host}")
    lateinit var host: String

    @Value("\${test.datasource.postgres.port}")
    lateinit var port: String

    @Value("\${test.datasource.postgres.username:postgres}")
    lateinit var username: String

    @Value("\${test.datasource.postgres.password}")
    lateinit var password: String

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
            host,
            port.toInt(),
            username,
            password,
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

        val jdbcUrl = PostgresJdbcUrlBuilder().create(host, port.toInt(), "postgres")
        val dataSource = DataSourceUtils.createDataSource(jdbcUrl, username, password)
        val databaseManager = PostgresDatabaseManager(dataSource)
        databaseManager.findAllNonSystemSchemas().forEach { databaseManager.deleteSchema(it.username) }
    }
}
