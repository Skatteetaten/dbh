package no.skatteetaten.aurora.databasehotel.dao.oracle

import com.zaxxer.hikari.HikariDataSource
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils

object OracleDataSourceUtils {

    /**
     * @param jdbcUrl
     * @param username
     * @param password
     * @param oracleScriptRequired This option may be required by some Oracle database installations (for example if
     * you run an oracle instance from a Docker image with default config) to be able to
     * create schema names without prefixing them with C##.
     * @return
     */
    fun createDataSource(
        jdbcUrl: String,
        username: String,
        password: String,
        oracleScriptRequired: Boolean
    ): HikariDataSource {

        val hikariConfig = DataSourceUtils.createConfig(jdbcUrl, username, password, 2)
        if (oracleScriptRequired) {
            hikariConfig.connectionInitSql = "alter session set \"_ORACLE_SCRIPT\"=true"
        }

        return DataSourceUtils.createDataSource(hikariConfig)
    }
}
