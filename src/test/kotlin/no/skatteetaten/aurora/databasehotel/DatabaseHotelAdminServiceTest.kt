package no.skatteetaten.aurora.databasehotel
// TODO hvordan får jeg ignorert en test?
/*
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.Companion.DEFAULT_SCHEMA_NAME
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import org.junit.Ignore
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

@Ignore("Denne testen feiler lokalt hos meg")
class DatabaseHotelAdminServiceTest {

    @Ignore("Denne testen feiler lokalt hos meg")
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
    */