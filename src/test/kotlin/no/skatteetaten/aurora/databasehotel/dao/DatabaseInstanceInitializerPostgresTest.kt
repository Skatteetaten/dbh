package no.skatteetaten.aurora.databasehotel.dao

import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.DatabaseTest
import no.skatteetaten.aurora.databasehotel.PostgresConfig
import no.skatteetaten.aurora.databasehotel.TargetEngine
import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import no.skatteetaten.aurora.databasehotel.service.createSchemaNameAndPassword
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

@DatabaseTest
class DatabaseInstanceInitializerPostgresTest {

    @Autowired
    lateinit var postgresConfig: PostgresConfig

    @Autowired
    @TargetEngine(POSTGRES)
    lateinit var postgresDataSource: DataSource

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
}