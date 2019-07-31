package no.skatteetaten.aurora.databasehotel.dao

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isGreaterThan
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.OracleConfig
import no.skatteetaten.aurora.databasehotel.OracleTest
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.service.createSchemaNameAndPassword
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import javax.sql.DataSource

@DatabaseTest
@OracleTest
class DatabaseInstanceInitializerOracleTest @Autowired constructor(
    val oracleConfig: OracleConfig,
    @TargetEngine(ORACLE) val oracleDataSource: DataSource,
    val initializer: DatabaseInstanceInitializer
) {

    @Test
    fun `migrate oracle database`() {

        val manager = OracleDatabaseManager(oracleDataSource)
        val (username, password) = createSchemaNameAndPassword()

        val schemaName = manager.createSchema(username, password)
        val jdbcUrl = OracleJdbcUrlBuilder(oracleConfig.service)
            .create(oracleConfig.host, oracleConfig.port.toInt(), null)

        val dataSource = DataSourceUtils.createDataSource(jdbcUrl, schemaName, password)
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