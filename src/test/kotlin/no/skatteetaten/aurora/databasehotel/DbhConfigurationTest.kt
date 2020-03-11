package no.skatteetaten.aurora.databasehotel

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@EnableConfigurationProperties
@ContextConfiguration(classes = [DbhConfiguration::class])
@TestPropertySource(
    properties = [
        "database-config.defaultInstanceName=postgres",
        "database-config.databases[0].host=localhost",
        "database-config.databases[0].port=5432",
        "database-config.databases[0].engine=postgres",
        "database-config.databases[0].instanceName=postgres",
        "database-config.databases[0].createSchemaAllowed=true",
        "database-config.databases[0].username=postgres",
        "database-config.databases[0].password=postgres",
        "database-config.databases[1].host=host2",
        "database-config.databases[1].port=5432",
        "database-config.databases[1].engine=postgres",
        "database-config.databases[1].instanceName=postgres",
        "database-config.databases[1].createSchemaAllowed=true",
        "database-config.databases[1].username=postgres2",
        "database-config.databases[1].password=postgres2"
    ]
)
class DbhConfigurationTest @Autowired constructor(val dbhConfiguration: DbhConfiguration) {

    @Test
    fun `binds properties as expected`() {

        assertThat(dbhConfiguration::databases).hasSize(2)
        assertThat(dbhConfiguration.databases[0]).isEqualTo(
            mapOf(
                "host" to "localhost",
                "port" to "5432",
                "engine" to "postgres",
                "instanceName" to "postgres",
                "createSchemaAllowed" to "true",
                "username" to "postgres",
                "password" to "postgres"
            )
        )
        assertThat(dbhConfiguration.databases[1]).containsAll(
            "host" to "host2",
            "engine" to "postgres",
            "username" to "postgres2",
            "password" to "postgres2"
        )
    }
}
