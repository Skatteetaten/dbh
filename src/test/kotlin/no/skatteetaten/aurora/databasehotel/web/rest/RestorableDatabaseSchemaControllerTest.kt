package no.skatteetaten.aurora.databasehotel.web.rest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.contentTypeJson
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.patch
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpHeaders

@WebMvcTest(RestorableDatabaseSchemaController::class)
class RestorableDatabaseSchemaControllerTest : AbstractControllerTest() {

    @MockkBean
    private lateinit var databaseHotelService: DatabaseHotelService

    @Test
    fun `Find all restorable schemas`() {
        every { databaseHotelService.findAllInactiveDatabaseSchemas(any()) } returns setOf(DatabaseSchemaTestBuilder().build())

        mockMvc.get(Path("/api/v1/restorableSchema/?labels=aurora")) {
            statusIsOk()
            responseJsonPath("$.status").equalsValue("OK")
            responseJsonPath("$.items[0].databaseSchema.id").equalsValue("123")
        }
    }

    @Test
    fun `Update restorable schema`() {
        every { databaseHotelService.findSchemaById(any(), any()) } returns
            Pair(DatabaseSchemaTestBuilder().build(), mockk(relaxed = true))

        mockMvc.patch(
            path = Path("/api/v1/restorableSchema/{id}", "123"),
            headers = HttpHeaders().contentTypeJson(),
            body = RestoreDatabaseSchemaPayload(true)
        ) {
            statusIsOk()
        }
    }
}
