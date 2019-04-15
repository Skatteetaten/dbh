package no.skatteetaten.aurora.databasehotel

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.ResultSet
import java.sql.SQLException

class SimplePostgresTest {

    @Test
    @Throws(SQLException::class)
    fun testSimple() {
        PostgreSQLContainer<Nothing>().use { postgres ->
            postgres.start()

            val resultSet = performQuery(postgres, "SELECT 1")

            val resultSetInt = resultSet.getInt(1)
            assertThat(resultSetInt).isEqualTo(1)
        }
    }

    @Throws(SQLException::class)
    private fun performQuery(container: JdbcDatabaseContainer<*>, sql: String): ResultSet {
        val hikariConfig = HikariConfig()
        hikariConfig.jdbcUrl = container.getJdbcUrl()
        hikariConfig.username = container.getUsername()
        hikariConfig.password = container.getPassword()

        val ds = HikariDataSource(hikariConfig)
        val statement = ds.connection.createStatement()
        statement.execute(sql)
        val resultSet = statement.resultSet
        resultSet.next()
        return resultSet
    }
}