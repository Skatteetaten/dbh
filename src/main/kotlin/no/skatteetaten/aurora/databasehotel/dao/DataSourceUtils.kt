package no.skatteetaten.aurora.databasehotel.dao

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils

private val logger = KotlinLogging.logger {}

private val LOGIN_TIMEOUT_SEC = 10

object DataSourceUtils {

    @JvmOverloads
    fun createDataSource(
        jdbcUrl: String,
        username: String,
        password: String,
        maximumPoolSize: Int = 2,
        loginTimeoutSec: Int = LOGIN_TIMEOUT_SEC
    ): HikariDataSource {

        val config = createConfig(jdbcUrl, username, password, maximumPoolSize)

        return createDataSource(config, loginTimeoutSec)
    }

    fun createDataSource(config: HikariConfig, loginTimeoutSec: Int = LOGIN_TIMEOUT_SEC): HikariDataSource {

        val maskedPassword = createPasswordHint(config.password)
        logger
            .debug(
                "Creating datasource using jdbcUrl: \"{}\", username: \"{}\", password: \"{}\"", config.jdbcUrl,
                config.username, maskedPassword
            )

        val hikariDataSource = HikariDataSource(config)
        // Setting this will prevent establishing connections from hanging forever in cases where the server is unable
        // to report back a connection error (for instance when under heavy load).
        hikariDataSource.loginTimeout = loginTimeoutSec
        return hikariDataSource
    }

    fun createConfig(jdbcUrl: String, username: String, password: String, maximumPoolSize: Int): HikariConfig {

        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.maximumPoolSize = maximumPoolSize

        // Just some defaults. Has not been properly evaluated.
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        return config
    }

    fun createPasswordHint(password: String?): String {
        return if (password == null || password.length < 8) {
            StringUtils.repeat("*", password?.length ?: 0)
        } else {
            password.substring(0, 2) + StringUtils.repeat(
                "*",
                password.length - 4
            ) + password
                .substring(password.length - 2)
        }
    }
}
