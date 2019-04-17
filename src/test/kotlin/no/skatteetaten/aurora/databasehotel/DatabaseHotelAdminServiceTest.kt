package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.Companion.DEFAULT_SCHEMA_NAME
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.jdbc.core.JdbcTemplate
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

    @Test
    fun a() {

        println(testHost)
        println(testPort)

        val databaseInstanceInitializer = DatabaseInstanceInitializer(DEFAULT_SCHEMA_NAME.toLowerCase())
        val databaseHotelAdminService = DatabaseHotelAdminService(databaseInstanceInitializer, 6, 1, "postgres", 10000)

        databaseHotelAdminService.registerPostgresDatabaseInstance(
            "postgres",
            testHost,
            testPort.toInt(),
            "postgres",
            testPassword,
            true,
            mapOf()
        )

        val instance = databaseHotelAdminService.findDatabaseInstanceByInstanceName("postgres")
            ?: throw AssertionError("Should be able to get database instance")

        val schema = instance.createSchema(emptyMap())
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        val dataSource = DataSourceUtils.createDataSource(
            schema.jdbcUrl,
            user.name,
            user.password,
            1
        )

        JdbcTemplate(dataSource).execute("create table test(id integer not null);")
    }
}
