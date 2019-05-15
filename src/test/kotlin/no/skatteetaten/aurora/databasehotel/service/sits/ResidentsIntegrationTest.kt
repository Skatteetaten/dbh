package no.skatteetaten.aurora.databasehotel.service.sits

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.OracleTest
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Date
import javax.sql.DataSource

@DatabaseTest
@OracleTest
class ResidentsIntegrationTest(
    @TargetEngine(ORACLE) val dataSource: DataSource
) {

    val residentsIntegration = ResidentsIntegration(dataSource)

    val schema = DatabaseSchema(
        "id",
        DatabaseInstanceMetaInfo(ORACLE, "A", "B", 1234, true, emptyMap()),
        "jdbc",
        "name",
        Date(),
        Date(),
        DatabaseSchemaMetaData(0.0)
    )

    val jdbcTemplate = JdbcTemplate(dataSource)

    @Test
    fun `fails when required labels not specified`() {

        assertThat { residentsIntegration.onSchemaCreated(schema) }
            .thrownError { hasClass(IllegalArgumentException::class) }
    }

    @Test
    fun `inserts into resident table onSchemaCreated`() {

        schema.labels = mapOf(
            "userId" to "some_id",
            "affiliation" to "aurora",
            "environment" to "dev",
            "application" to "dbh",
            "name" to "db"
        )

        jdbcTemplate.execute("delete from RESIDENTS.RESIDENTS")

        residentsIntegration.onSchemaCreated(schema)

        val resident = jdbcTemplate.queryForMap("select * from RESIDENTS.RESIDENTS")
        assertThat(resident["RESIDENT_NAME"]).isEqualTo(schema.name)
        assertThat(resident["RESIDENT_EMAIL"]).isEqualTo("some_id")
        assertThat(resident["RESIDENT_SERVICE"]).isEqualTo("aurora/dev/dbh/db")
    }
}