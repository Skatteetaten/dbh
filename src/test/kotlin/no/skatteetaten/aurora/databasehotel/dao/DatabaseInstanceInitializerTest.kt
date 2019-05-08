package no.skatteetaten.aurora.databasehotel.dao

import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.OracleConfig
import no.skatteetaten.aurora.databasehotel.PostgresConfig
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.TestDataSources
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.service.createSchemaNameAndPassword
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import javax.sql.DataSource

@ExtendWith(SpringExtension::class)
@JsonTest
@ContextConfiguration(classes = [PostgresConfig::class, OracleConfig::class, TestDataSources::class, DatabaseInstanceInitializer::class])
class DatabaseInstanceInitializerTest {

    @Autowired
    lateinit var postgresConfig: PostgresConfig

    @Autowired
    @TargetEngine(POSTGRES)
    lateinit var postgresDataSource: DataSource

    @Autowired
    lateinit var oracleConfig: OracleConfig

    @Autowired
    @TargetEngine(ORACLE)
    lateinit var oracleDataSource: DataSource

    @Autowired
    lateinit var initializer: DatabaseInstanceInitializer


    @Test
    fun `migrate postgres database`() {

        val manager = PostgresDatabaseManager(postgresDataSource)
        val (username, password) = createSchemaNameAndPassword()

        val schemaName = manager.createSchema(username, password)
        val jdbcUrl = PostgresJdbcUrlBuilder().create(postgresConfig.host, postgresConfig.port.toInt(), schemaName)

        val dataSource = DataSourceUtils.createDataSource(jdbcUrl, schemaName, password)
        initializer.migrate(dataSource)
    }

    @Test
    fun `migrate oracle database`() {

        val manager = OracleDatabaseManager(oracleDataSource)
        val (username, password) = createSchemaNameAndPassword()

        val schemaName = manager.createSchema(username, password)
        val jdbcUrl = OracleJdbcUrlBuilder(oracleConfig.service).create(oracleConfig.host, oracleConfig.port.toInt(), null)

        val dataSource = DataSourceUtils.createDataSource(jdbcUrl, schemaName, password)
        initializer.migrate(dataSource)
    }
}