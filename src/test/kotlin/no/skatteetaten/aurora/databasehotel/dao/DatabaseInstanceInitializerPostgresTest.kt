package no.skatteetaten.aurora.databasehotel.dao

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isGreaterThan
import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.PostgresCleaner
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.createPostgresSchema
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate

@DatabaseTest
class DatabaseInstanceInitializerPostgresTest @Autowired constructor(
    @TargetEngine(POSTGRES) val dataSource: HikariDataSource,
    val initializer: DatabaseInstanceInitializer
) {

    @Test
    fun `migrate postgres database`() {

        val schemaDataSource = createPostgresSchema(dataSource)
        val jdbcTemplate = JdbcTemplate(schemaDataSource)

        assertThat { jdbcTemplate.queryForList("select * from FLYWAY_SCHEMA_HISTORY") }
            .isFailure().hasClass(BadSqlGrammarException::class)

        initializer.migrate(schemaDataSource)

        val migrations = jdbcTemplate.queryForList("select * from FLYWAY_SCHEMA_HISTORY")
        assertThat(migrations.size).isGreaterThan(0)
        migrations.forEach {
            assertThat(it["success"]).isEqualTo(true)
        }

        PostgresCleaner(dataSource).cleanDataSourceSchema(schemaDataSource)
    }
}
