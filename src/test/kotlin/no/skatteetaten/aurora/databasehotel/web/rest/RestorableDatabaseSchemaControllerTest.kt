package no.skatteetaten.aurora.databasehotel.web.rest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import java.util.Date

@WebMvcTest(RestorableDatabaseSchemaController::class)
class RestorableDatabaseSchemaControllerTest : AbstractControllerTest() {

    @MockkBean
    private lateinit var databaseHotelService: DatabaseHotelService

    @Test
    fun `Find all restorable schemas`() {
        val schema = DatabaseSchema(
            id = "123",
            active = true,
            databaseInstanceMetaInfo = DatabaseInstanceMetaInfo(
                DatabaseEngine.ORACLE, "instance", "host", 123, true,
                emptyMap()
            ),
            jdbcUrl = "jdbcUrl",
            name = "name",
            createdDate = Date(),
            lastUsedDate = null,
            setToCooldownAt = Date(),
            deleteAfter = Date(),
            metadata = null
        )
        every { databaseHotelService.findAllInactiveDatabaseSchemas(any()) } returns setOf(schema)

        mockMvc.get(Path("/api/v1/restorableSchema/?labels=aurora")) {
            statusIsOk()
                .responseJsonPath("$.status").equalsValue("OK")
                .responseJsonPath("$.items[0].databaseSchema.id").equalsValue("123")
        }
    }
}