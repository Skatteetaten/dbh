package no.skatteetaten.aurora.databasehotel.dao

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isGreaterThan
import com.zaxxer.hikari.HikariDataSource
import java.math.BigDecimal
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.OracleTest
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.createOracleSchema
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate

@DatabaseTest
@OracleTest
class DatabaseInstanceInitializerOracleTest @Autowired constructor(
    @TargetEngine(ORACLE) val oracleDataSource: HikariDataSource,
    val initializer: DatabaseInstanceInitializer
) {

    @Test
    fun `migrate oracle database`() {

        val dataSource = createOracleSchema(oracleDataSource)
        val jdbcTemplate = JdbcTemplate(dataSource)

        assertThat { jdbcTemplate.queryForList("select * from SCHEMA_VERSION") }
            .isFailure().hasClass(BadSqlGrammarException::class)

        initializer.migrate(dataSource)

        val migrations = jdbcTemplate.queryForList("select * from SCHEMA_VERSION")
        assertThat(migrations.size).isGreaterThan(0)
        migrations.forEach {
            assertThat(it["success"]).isEqualTo(BigDecimal.ONE)
        }
    }
}
