package no.skatteetaten.aurora.databasehotel.web.rest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import no.skatteetaten.aurora.databasehotel.web.security.SharedSecretReader
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.test.web.servlet.MockMvc

@AutoConfigureRestDocs
abstract class AbstractControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var sharedSecretReader: SharedSecretReader

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
}
