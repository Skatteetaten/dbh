package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.postgres.PostgresDatabaseManager
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import javax.sql.DataSource

class PostgresConnectionsExtension : BeforeAllCallback, AfterAllCallback {
    lateinit var databaseManager: PostgresDatabaseManager

    override fun beforeAll(context: ExtensionContext) {
        databaseManager = databaseManager(context)
        setConnectionLimit(20)
    }

    override fun afterAll(context: ExtensionContext) {
        databaseManager = databaseManager(context)
        setConnectionLimit(6)
    }

    private fun setConnectionLimit(limit: Int) {
        databaseManager.executeStatements(
            """DO ${'$'}${'$'}
                BEGIN
                    IF EXISTS (
                        SELECT FROM pg_catalog.pg_roles
                        WHERE rolname = 'hadmin') THEN
                        
                        ALTER ROLE hadmin WITH CONNECTION LIMIT $limit;
                    END IF;
                END
                ${'$'}${'$'};
            """.trimMargin()
        )
    }

    private fun databaseManager(context: ExtensionContext): PostgresDatabaseManager {
        val dataSource: DataSource = SpringExtension.getApplicationContext(context)
            .getBean("postgresDatasource") as DataSource
        return PostgresDatabaseManager(dataSource)
    }
}
