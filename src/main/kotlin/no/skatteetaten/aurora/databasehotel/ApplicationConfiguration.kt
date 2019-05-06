package no.skatteetaten.aurora.databasehotel

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableAsync
@EnableScheduling
class ApplicationConfiguration(val dbhInitializer: DbhInitializer) : InitializingBean {

    override fun afterPropertiesSet() {

        dbhInitializer.configure()
    }
}