package no.skatteetaten.aurora.databasehotel.web.rest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.post
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest

@WebMvcTest(DatabaseInstanceController::class)
class DatabaseInstanceControllerTest : AbstractControllerTest() {

    @MockkBean
    private lateinit var databaseHotelAdminService: DatabaseHotelAdminService

    @Test
    fun `List instances`() {
        every { databaseHotelAdminService.findAllDatabaseInstances() } returns setOf(DatabaseInstanceBuilder().build())

        mockMvc.get(Path("/api/v1/admin/databaseInstance/")) {
            statusIsOk()
            responseJsonPath("$.status").equalsValue("OK")
            responseJsonPath("$.items[0].engine").equalsValue("ORACLE")
            responseJsonPath("$.items[0].labels.affiliation").equalsValue("aurora")
        }
    }

}
