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
            5432,
            "postgres",
            "ar3nda1",
            true,
            mapOf()
        )

        val instance = databaseHotelAdminService.findDatabaseInstanceByInstanceName("postgres")!!
        val schema = instance.createSchema(emptyMap())

        schema.users.forEach {
            val dataSource =
                DataSourceUtils.createDataSource("jdbc:postgresql://localhost:5432/${it.name}", it.name, it.password, 1)

            JdbcTemplate(dataSource).execute("create table test(id integer not null);")
        }
    }
}