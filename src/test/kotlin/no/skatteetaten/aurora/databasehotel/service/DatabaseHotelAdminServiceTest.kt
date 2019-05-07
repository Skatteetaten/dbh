package no.skatteetaten.aurora.databasehotel.service

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import no.skatteetaten.aurora.databasehotel.DomainUtils.metaInfo
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DatabaseHotelAdminServiceTest {

    private val adminService = DatabaseHotelAdminService(DatabaseInstanceInitializer(), 6, 1, "db", 300000L)

    @BeforeEach
    fun setup() {
        adminService.removeAllInstances()
    }

    @Test
    fun `register database instance`() {

        assertThat(adminService.findAllDatabaseInstances()).isEmpty()
        adminService.registerDatabaseInstance(createMockInstance("prod"))
        assertThat(adminService.findAllDatabaseInstances()).hasSize(1)
    }

    @Test
    fun `find by instanceName and hostname`() {

        listOf("prod", "test").forEach(::register)

        assertThat(adminService.findDatabaseInstanceByHost("prod-host")?.instanceName).isEqualTo("prod")
        assertThat(adminService.findDatabaseInstanceByHost("test-host")?.instanceName).isEqualTo("test")
        assertThat(adminService.findDatabaseInstanceByHost("no-such-host")).isNull()

        assertThat(adminService.findDatabaseInstanceByInstanceName("prod")?.instanceName).isEqualTo("prod")
        assertThat(adminService.findDatabaseInstanceByInstanceName("test")?.instanceName).isEqualTo("test")
        assertThat(adminService.findDatabaseInstanceByInstanceName("no-such")).isNull()
    }

    @Test
    fun `getting random instance ignores instances where isCreateSchmeaAllowed is false`() {

        listOf(
            createMockInstance("dev1", true, ORACLE),
            createMockInstance("dev2", true, ORACLE),
            createMockInstance("dev3", false, ORACLE)
        ).forEach(::register)
        val usedInstances = (0..1000).map { adminService.findDatabaseInstanceOrFail().instanceName }.toSet()

        assertThat(usedInstances).isEqualTo(setOf("dev1", "dev2"))
    }

    @Test
    fun `random instance matches requirements when specifying engine`() {

        listOf(
            createMockInstance("dev1", true, POSTGRES),
            createMockInstance("dev2", true, ORACLE),
            createMockInstance("dev3", false, POSTGRES),
            createMockInstance("dev4", true, ORACLE),
            createMockInstance("dev5", true, POSTGRES),
            createMockInstance("dev6", false, ORACLE)
        ).forEach(::register)

        val usedInstances = (0..1000)
            .map { adminService.findDatabaseInstanceOrFail(DatabaseInstanceRequirements(POSTGRES)).instanceName }
            .toSet()

        assertThat(usedInstances).isEqualTo(setOf("dev1", "dev5"))
    }

    @Test
    fun `instance matches requirements`() {

        listOf(
            createMockInstance("dev1", true, POSTGRES, mapOf("type" to "prod")),
            createMockInstance("dev2", true, ORACLE),
            createMockInstance("dev3", false, POSTGRES),
            createMockInstance("dev4", true, ORACLE),
            createMockInstance("dev5", true, POSTGRES, mapOf("type" to "test")),
            createMockInstance("dev6", false, ORACLE)
        ).forEach(::register)

        val usedInstances = (0..1000)
            .map {
                val requirements = DatabaseInstanceRequirements(POSTGRES, instanceLabels = mapOf("type" to "prod"))
                adminService.findDatabaseInstanceOrFail(requirements).instanceName
            }
            .toSet()

        assertThat(usedInstances).isEqualTo(setOf("dev1"))
    }

    @Test
    fun `fails when no instance matches`() {

        val requirements = DatabaseInstanceRequirements(POSTGRES, instanceLabels = mapOf("type" to "prod"))
        assertThat { adminService.findDatabaseInstanceOrFail(requirements) }
            .thrownError { hasClass(DatabaseServiceException::class) }
    }

    private fun register(instance: DatabaseInstance) {
        adminService.registerDatabaseInstance(instance)
    }

    private fun register(name: String) {
        adminService.registerDatabaseInstance(createMockInstance(name))
    }
}

private fun createMockInstance(
    name: String,
    isCreateSchemaAllowed: Boolean = true,
    engine: DatabaseEngine = POSTGRES,
    labels: Map<String, String> = emptyMap()
): DatabaseInstance {
    return mockk {
        every { metaInfo } returns metaInfo(name, "$name-host", 1521, isCreateSchemaAllowed, engine, labels)
        every { instanceName } returns name
        every { isCreateSchemaAllowed() } returns isCreateSchemaAllowed
    }
}
