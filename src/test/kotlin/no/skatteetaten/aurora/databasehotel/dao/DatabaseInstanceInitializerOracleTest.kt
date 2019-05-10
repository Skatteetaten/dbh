package no.skatteetaten.aurora.databasehotel.dao

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
import javax.sql.DataSource

@DatabaseTest
@OracleTest
class DatabaseInstanceInitializerOracleTest {

    @Autowired
    lateinit var oracleConfig: OracleConfig

    @Autowired
    @TargetEngine(ORACLE)
    lateinit var oracleDataSource: DataSource

    @Autowired
    lateinit var initializer: DatabaseInstanceInitializer

    @Test
    fun `migrate oracle database`() {

        val manager = OracleDatabaseManager(oracleDataSource)
        val (username, password) = createSchemaNameAndPassword()

        val schemaName = manager.createSchema(username, password)
        val jdbcUrl =
            OracleJdbcUrlBuilder(oracleConfig.service).create(oracleConfig.host, oracleConfig.port.toInt(), null)

        val dataSource = DataSourceUtils.createDataSource(jdbcUrl, schemaName, password)
        initializer.migrate(dataSource)
    }
}