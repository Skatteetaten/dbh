package no.skatteetaten.aurora.databasehotel.web.rest

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.contentTypeJson
import no.skatteetaten.aurora.mockmvc.extensions.get
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.net.URLEncoder.encode
import java.util.stream.Stream

@WebMvcTest(value = [DatabaseSchemaController::class, ErrorHandler::class])
class DatabaseSchemaControllerTest : AbstractControllerTest() {

    @MockkBean
    private lateinit var databaseHotelService: DatabaseHotelService

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
        every { databaseHotelService.validateConnection(any()) } returns true
        every { databaseHotelService.validateConnection(any(), any(), any()) } returns true

        mockMvc.put(
            path = Path("/api/v1/schema/validate"),
            body = request,
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
        mockMvc.put(
            path = Path("/api/v1/schema/validate"),
            headers = HttpHeaders().contentTypeJson(),
            body = ConnectionVerificationRequest()
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
        mockMvc.put(
            path = Path("/api/v1/schema/123"),
            body = SchemaCreationRequest(schema = Schema("", "", "")),
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `validate jdbc input for create database schema`() {
        mockMvc.post(
            path = Path("/api/v1/schema/"),
            docsIdentifier = "post-schema-bad-request",
            body = SchemaCreationRequest(schema = Schema("", "", "")),
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `create schema`() {
        every { databaseHotelService.createSchema(any(), any()) } returns DatabaseSchemaTestBuilder().build()

        mockMvc.post(
            path = Path("/api/v1/schema/"),
            body = SchemaCreationRequest(),
            headers = HttpHeaders().contentTypeJson()
        ) {
            statusIsOk()
                .responseJsonPath("$.status").equalsValue("OK")
                .responseJsonPath("$.totalCount").equalsValue(1)
                .responseJsonPath("$.items.length()").equalsValue(1)
        }
    }

    @Test
    fun `Create external schema`() {
        every { databaseHotelService.createSchema(any(), any()) } returns DatabaseSchemaTestBuilder(type = DatabaseSchema.Type.EXTERNAL).build()

        mockMvc.post(
            path = Path("/api/v1/schema/"),
            body = SchemaCreationRequest(),
            docsIdentifier = "post-schema-external",
            headers = HttpHeaders().contentTypeJson()
        ) {
            statusIsOk()
                .responseJsonPath("$.status").equalsValue("OK")
                .responseJsonPath("$.totalCount").equalsValue(1)
                .responseJsonPath("$.items.length()").equalsValue(1)
        }
    }

    @Test
    fun `create schema throws exception`() {
        val dbErrorMessage = "ORA-00059: maximum number of DB_FILES exceeded"
        every { databaseHotelService.createSchema(any(), any()) } throws DataAccessException(dbErrorMessage)

        mockMvc.post(
            path = Path("/api/v1/schema/"),
            docsIdentifier = "post-schema-db-error",
            body = SchemaCreationRequest(),
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.INTERNAL_SERVER_ERROR)
            responseJsonPath("$.status").equalsValue("Failed")
            responseJsonPath("$.totalCount").equalsValue(1)
            responseJsonPath("$.items[0]").equalsValue(dbErrorMessage)
        }
    }

    @Test
    fun `Get schema by id`() {
        every { databaseHotelService.findSchemaById(any(), any()) } returns Pair(
            DatabaseSchemaTestBuilder().build(),
            DatabaseInstanceBuilder().build()
        )

        mockMvc.get(Path("/api/v1/schema/{id}", "123")) {
            statusIsOk()
            responseJsonPath("$.status").equalsValue("OK")
            responseJsonPath("$.totalCount").equalsValue(1)
            responseJsonPath("$.items[0].id").equalsValue("123")
        }
    }

    @Test
    fun `Get all schemas`() {
        every { databaseHotelService.findAllDatabaseSchemas(any(), any(), any()) } returns
            setOf(DatabaseSchemaTestBuilder().build())

        mockMvc.get(Path("/api/v1/schema/")) {
            statusIsOk()
            responseJsonPath("$.status").equalsValue("OK")
            responseJsonPath("$.totalCount").equalsValue(1)
            responseJsonPath("$.items[0].id").equalsValue("123")
        }
    }
}
