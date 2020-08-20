package no.skatteetaten.aurora.databasehotel.dao

import com.google.common.base.Strings.nullToEmpty
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils

private val logger = KotlinLogging.logger {}

object DataSourceUtils {

    @JvmOverloads
    fun createDataSource(
        jdbcUrl: String,
        username: String,
        password: String,
        maximumPoolSize: Int = 2
    ): HikariDataSource {

        val config = createConfig(jdbcUrl, username, password, maximumPoolSize)

        return createDataSource(config)
    }

    fun createDataSource(config: HikariConfig): HikariDataSource {

        val maskedPassword = createPasswordHint(config.password)
        logger
            .info(
                "Creating datasource using jdbcUrl: \"{}\", username: \"{}\", password: \"{}\"", config.jdbcUrl,
                config.username, maskedPassword
            )

        return HikariDataSource(config)
    }

    fun createConfig(jdbcUrl: String, username: String, password: String, maximumPoolSize: Int): HikariConfig {

        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.maximumPoolSize = maximumPoolSize
        logger.info("{}", config)

        // Just some defaults. Has not been properly evaluated.
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        return config
    }

    fun createPasswordHint(password: String?): String {
        return if (password == null || password.length < 8) {
            StringUtils.repeat("*", nullToEmpty(password).length)
        } else {
            password.substring(0, 2) + StringUtils.repeat(
                "*",
                password.length - 4
            ) + password
                .substring(password.length - 2)
        }
    }
}
