package no.skatteetaten.aurora.databasehotel.web.rest

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.contentTypeJson
import no.skatteetaten.aurora.mockmvc.extensions.post
import no.skatteetaten.aurora.mockmvc.extensions.put
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.status
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup
import java.net.URLEncoder.encode
import java.util.stream.Stream

class DatabaseSchemaControllerTest2 {

    class Params : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            "affiliation=aurora,application=test1" to mapOf("affiliation" to "aurora", "application" to "test1"),
            "affiliation=aurora , application" to mapOf("affiliation" to "aurora", "application" to null),
            "affiliation= aurora , application = " to mapOf("affiliation" to "aurora", "application" to null),
            "affiliation= aurora ,=something" to mapOf("affiliation" to "aurora"),
            "affiliation,application" to mapOf("affiliation" to null, "application" to null),
            "" to emptyMap()
        ).map { Arguments.of(it.first, it.second) }
    }

    @ParameterizedTest
    @ArgumentsSource(Params::class)
    fun verifyLabelsParsing(labelString: String, expectedLabels: Map<String, String>) {
        assertThat(parseLabelsParam(encode(labelString, "UTF-8"))).isEqualTo(expectedLabels)
    }

    class JdbcParams : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            ConnectionVerificationRequest(id = "1234"),
            ConnectionVerificationRequest(jdbcUser = JdbcUser(username = "user", jdbcUrl = "url", password = "123"))
        )
            .map {
                Arguments.of(it)
            }
    }

    @ParameterizedTest
    @ArgumentsSource(JdbcParams::class)
    fun validateConnection(request: ConnectionVerificationRequest) {
        val json = jacksonObjectMapper().writeValueAsString(request)
        val databaseHotelService = mockk<DatabaseHotelService>().apply {
            every { validateConnection(any()) } returns true
            every { validateConnection(any(), any(), any()) } returns true
        }
        val databaseSchemaController = DatabaseSchemaController(databaseHotelService, true, true)
        val mockMvc = standaloneSetup(databaseSchemaController).build()
        mockMvc.put(
            path = Path("/api/v1/schema/validate"),
            body = json,
            headers = HttpHeaders().contentTypeJson()
        ) {
            statusIsOk()
                .responseJsonPath("$.status").equalsValue("OK")
                .responseJsonPath("$.totalCount").equalsValue(1)
                .responseJsonPath("$.items[0]").isTrue()
        }
    }

    @Test
    fun `validate connection given null values`() {
        val json = jacksonObjectMapper().writeValueAsString(ConnectionVerificationRequest())

        val databaseSchemaController = DatabaseSchemaController(
            databaseHotelService = mockk(),
            schemaListingAllowed = true,
            dropAllowed = true
        )
        val mockMvc = standaloneSetup(ErrorHandler(), databaseSchemaController).build()
        mockMvc.put(
            path = Path("/api/v1/schema/validate"),
            body = json,
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.BAD_REQUEST)
                .responseJsonPath("$.status").equalsValue("Failed")
                .responseJsonPath("$.totalCount").equalsValue(1)
                .responseJsonPath("$.items.length()").equalsValue(1)
                .responseJsonPath("$.items[0]").equalsValue("id or jdbcUser is required")
        }
    }

    @Test
    fun `validate jdbc input for update database schema`() {
        val input = SchemaCreationRequest(schema = Schema("", "", ""))
        val json = jacksonObjectMapper().writeValueAsString(input)

        val databaseSchemaController = DatabaseSchemaController(mockk(), true, true)
        val mockMvc = standaloneSetup(ErrorHandler(), databaseSchemaController).build()
        mockMvc.put(
            path = Path("/api/v1/schema/123"),
            body = json,
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `validate jdbc input for create database schema`() {
        val json = jacksonObjectMapper().writeValueAsString(SchemaCreationRequest(schema = Schema("", "", "")))

        val databaseSchemaController = DatabaseSchemaController(
            databaseHotelService = mockk(),
            schemaListingAllowed = true,
            dropAllowed = true
        )
        val mockMvc = standaloneSetup(ErrorHandler(), databaseSchemaController).build()
        mockMvc.post(
            path = Path("/api/v1/schema/"),
            body = json,
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `create schema throws exception`() {
        val json = jacksonObjectMapper().writeValueAsString(SchemaCreationRequest())
        val dbErrorMessage = "ORA-00059: maximum number of DB_FILES exceeded"
        val databaseHotelService = mockk<DatabaseHotelService>().apply {
            every { createSchema(any(), any()) } throws DataAccessException(dbErrorMessage)
        }

        val databaseSchemaController = DatabaseSchemaController(
            databaseHotelService = databaseHotelService,
            schemaListingAllowed = true,
            dropAllowed = true
        )
        val mockMvc = standaloneSetup(ErrorHandler(), databaseSchemaController).build()
        mockMvc.post(
            path = Path("/api/v1/schema/"),
            body = json,
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.INTERNAL_SERVER_ERROR)
            responseJsonPath("$.status").equalsValue("Failed")
            responseJsonPath("$.totalCount").equalsValue(1)
            responseJsonPath("$.items[0]").equalsValue(dbErrorMessage)
        }
    }
}
