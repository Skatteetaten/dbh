@file:JvmName("Main")

package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.EnvVarMapper.mapEnvironmentVarsToSystemProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

@SpringBootApplication(exclude = [
    org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration::class,
    org.springframework.boot.actuate.autoconfigure.metrics.web.tomcat.TomcatMetricsAutoConfiguration::class,
    org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration::class,
    org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration::class,
    org.springframework.boot.actuate.autoconfigure.metrics.cache.CacheMetricsAutoConfiguration::class,
    org.springframework.boot.actuate.autoconfigure.metrics.web.client.HttpClientMetricsAutoConfiguration::class,
    org.springframework.boot.actuate.autoconfigure.metrics.web.servlet.WebMvcMetricsAutoConfiguration::class,
    DataSourceAutoConfiguration::class
])
class Application

fun main(args: Array<String>) {

    mapEnvironmentVarsToSystemProperties()
    SpringApplication.run(Application::class.java, *args)
}
