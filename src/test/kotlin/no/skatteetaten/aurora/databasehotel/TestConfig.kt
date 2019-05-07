package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.service.postgres.PostgresJdbcUrlBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TargetEngine(val value: DatabaseEngine)

@Configuration
class TestConfig2 {

    @Value("\${test.datasource.postgres.host}")
    lateinit var host: String

    @Value("\${test.datasource.postgres.port}")
    lateinit var port: String

    @Value("\${test.datasource.postgres.username:postgres}")
    lateinit var username: String

    @Value("\${test.datasource.postgres.password}")
    lateinit var password: String

    @Bean
    @TargetEngine(POSTGRES)
    fun postgresDatasource(): DataSource {

        return DataSourceUtils.createDataSource(
            PostgresJdbcUrlBuilder().create(host, port.toInt(), "postgres"),
            username,
            password,
            1
        )
    }
}