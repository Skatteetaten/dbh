package no.skatteetaten.aurora.databasehotel.web.rest

import assertk.assert
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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
        assert(parseLabelsParam(encode(labelString, "UTF-8"))).isEqualTo(expectedLabels)
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
        mockMvc.perform(put("/api/v1/schema/validate").content(json).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OK"))
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items[0]").value(true))
    }

    @Test
    fun `validate connection given null values`() {
        val json = jacksonObjectMapper().writeValueAsString(ConnectionVerificationRequest())

        val databaseSchemaController = DatabaseSchemaController(mockk(), true, true)
        val mockMvc = standaloneSetup(ErrorHandler(), databaseSchemaController).build()
        mockMvc.perform(put("/api/v1/schema/validate").content(json).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorMessage").value("id or jdbcUser is required"))
    }

    @Test
    fun `validate jdbc input for update database schema`() {
        val input = SchemaCreationRequest(schema = Schema("", "", ""))
        val json = jacksonObjectMapper().writeValueAsString(input)

        val databaseSchemaController = DatabaseSchemaController(mockk(), true, true)
        val mockMvc = standaloneSetup(ErrorHandler(), databaseSchemaController).build()
        mockMvc.perform(put("/api/v1/schema/123").content(json).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `validate jdbc input for create database schema`() {
        val input = SchemaCreationRequest(schema = Schema("", "", ""))
        val json = jacksonObjectMapper().writeValueAsString(input)

        val databaseSchemaController = DatabaseSchemaController(mockk(), true, true)
        val mockMvc = standaloneSetup(ErrorHandler(), databaseSchemaController).build()
        mockMvc.perform(post("/api/v1/schema/").content(json).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest)
    }
}
