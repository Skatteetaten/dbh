package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TargetEngine(val value: DatabaseEngine)

@Component
@ConfigurationProperties("test.datasource.postgres")
class PostgresConfig {
    lateinit var host: String
    lateinit var port: String
    lateinit var username: String
    lateinit var password: String
}

@Component
@ConfigurationProperties("test.datasource.oracle")
@ConditionalOnProperty(name = ["test.include-oracle-tests"], havingValue = "true", matchIfMissing = false)
class OracleConfig {
    lateinit var host: String
    lateinit var port: String
    lateinit var service: String
    lateinit var username: String
    lateinit var password: String
    lateinit var clientService: String
    lateinit var oracleScriptRequired: String
}

@Configuration
class TestDataSources {
    @Bean
    @TargetEngine(POSTGRES)
    fun postgresDatasource(postgresConfig: PostgresConfig): DataSource = DataSourceUtils.createDataSource(
        PostgresJdbcUrlBuilder().create(postgresConfig.host, postgresConfig.port.toInt(), "postgres"),
        postgresConfig.username,
        postgresConfig.password,
        1
    )

    @Bean
    @TargetEngine(ORACLE)
    @ConditionalOnBean(OracleConfig::class)
    fun oracleDatasource(oracleConfig: OracleConfig): DataSource = DataSourceUtils.createDataSource(
        OracleJdbcUrlBuilder(oracleConfig.service).create(oracleConfig.host, oracleConfig.port.toInt(), null),
        oracleConfig.username,
        oracleConfig.password,
        1
    )
}