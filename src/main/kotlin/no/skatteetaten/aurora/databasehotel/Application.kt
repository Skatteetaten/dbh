@file:JvmName("Main")

package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.EnvVarMapper.mapEnvironmentVarsToSystemProperties
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.transaction.annotation.Transactional

/*
@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class Application
*/

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class Application(val admin: DatabaseHotelAdminService) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments?) {

        val instances = admin.findAllDatabaseInstances()
        instances.filter { it.metaInfo.engine == DatabaseEngine.ORACLE }.forEach {
            val jdbcTemplate = (it.databaseHotelDataDao as DatabaseSupport).jdbcTemplate
            jdbcTemplate.execute("delete from SCHEMA_VERSION where \"checksum\" in (-1854067104, 1258639134)")
        }
    }
}

fun main(args: Array<String>) {

    mapEnvironmentVarsToSystemProperties()
    SpringApplication.run(Application::class.java, *args)
}
