package no.skatteetaten.aurora.databasehotel.service.oracle

import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.OracleConfig
import no.skatteetaten.aurora.databasehotel.OracleTest
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.deleteNonSystemSchemas
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@DatabaseTest
@OracleTest
class OracleResourceUsageCollectorTest @Autowired constructor(
    val oracleConfig: OracleConfig,
    @TargetEngine(DatabaseEngine.ORACLE) val dataSource: DataSource
) {
    private val testSchemas = listOf("TEST1", "TEST2")

    private val resourceUsageCollector = OracleResourceUsageCollector(dataSource, 10_000)

    @BeforeEach
    fun setup() {
        OracleDatabaseManager(dataSource).apply {
            deleteNonSystemSchemas()
            testSchemas.forEach { schemaName ->
                createSchema(schemaName, "-")
                val template = createUserJdbcTemplate(schemaName, "-")
                template.execute("create table $schemaName.DUMMY (id integer)")
                (1..10).forEach { template.execute("insert into $schemaName.DUMMY values ($it)") }
            }
        }
        resourceUsageCollector.invalidateCache()
    }

    @Test
    fun `schema size for schema`() {

        val schemaSize = resourceUsageCollector.getSchemaSize(testSchemas.first())

        assertThat(schemaSize).isNotNull()
        assertThat(schemaSize!!.schemaSizeMb.toDouble()).isGreaterThan(0.0)
    }

    @Test
    fun `schema sizes for all schemas`() {

        val schemaSizes = resourceUsageCollector.schemaSizes

        testSchemas.forEach { schema ->
            val schemaSize = schemaSizes.firstOrNull { it.owner == schema }
            assertThat(schemaSize).isNotNull()
            assertThat(schemaSize!!.schemaSizeMb.toDouble()).isGreaterThan(0.0)
        }
    }

    private fun createUserJdbcTemplate(username: String, password: String): JdbcTemplate {
        val uds = DataSourceUtils.createDataSource(
            OracleJdbcUrlBuilder(oracleConfig.service).create(oracleConfig.host, oracleConfig.port.toInt(), null),
            username,
            password
        )
        return JdbcTemplate(uds)
    }
}
