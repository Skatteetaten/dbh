@file:JvmName("Main")
package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.EnvVarMapper.mapEnvironmentVarsToSystemProperties

import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
@EnableAsync
@EnableScheduling
class Application(val dbhInitializer: DbhInitializer) : InitializingBean {

    override fun afterPropertiesSet() {

        dbhInitializer.configure()
    }
}

fun main(args: Array<String>) {

    mapEnvironmentVarsToSystemProperties()
    SpringApplication.run(Application::class.java, *args)
}
