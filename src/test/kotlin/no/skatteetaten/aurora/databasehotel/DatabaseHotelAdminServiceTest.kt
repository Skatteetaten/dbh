package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.Companion.DEFAULT_SCHEMA_NAME
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class DatabaseHotelAdminServiceTest {

    @Test
    fun a() {

        val databaseInstanceInitializer = DatabaseInstanceInitializer(DEFAULT_SCHEMA_NAME.toLowerCase())
        val databaseHotelAdminService = DatabaseHotelAdminService(databaseInstanceInitializer, 6, 1, "postgres", 10000)

        databaseHotelAdminService.registerPostgresDatabaseInstance(
            "postgres",
            "localhost",
            25432,
            "postgres",
            "ar3nda1",
            true,
            mapOf()
        )

        val instance = databaseHotelAdminService.findDatabaseInstanceByInstanceName("postgres")
            ?: throw AssertionError("Should be able to get database instance")

        val schema = instance.createSchema(emptyMap())
        val user = schema.users.firstOrNull() ?: throw AssertionError("Should be able to find a user")

        val dataSource = DataSourceUtils.createDataSource(
            "jdbc:postgresql://localhost:25432/${user.name}",
            user.name,
            user.password,
            1
        )

        JdbcTemplate(dataSource).execute("create table test(id integer not null);")
    }
}
