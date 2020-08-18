package no.skatteetaten.aurora.databasehotel.web.security

import no.skatteetaten.aurora.databasehotel.web.security.AuthState.AUTH_DISABLED
import no.skatteetaten.aurora.databasehotel.web.security.AuthState.AUTH_ENABLED
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController {
    @GetMapping
    fun index(authentication: Authentication?): ResponseEntity<Any> = ResponseEntity.ok(
        authentication?.let {
            mapOf(
                "principal" to it.principal,
                "authorities" to it.authorities.map { it.authority }
            )
        } ?: emptyMap<String, Any>())
}

enum class AuthState { AUTH_ENABLED, AUTH_DISABLED }

@Import(SharedSecretReader::class)
abstract class WebSecurityConfigTest(val mvc: MockMvc, val authEnabled: AuthState = AUTH_ENABLED) {

    @Test
    fun `access forbidden when no token provided`() {
        mvc.perform(get("/"))
            .andExpect(if (authEnabled == AUTH_ENABLED) status().isForbidden else status().isOk)
    }

    @Test
    fun `access forbidden when incorrect token provided`() {
        mvc.perform(get("/").header("Authorization", "bearer incorrect"))
            .andExpect(if (authEnabled == AUTH_ENABLED) status().isForbidden else status().isOk)
    }

    @Test
    fun `access granted when correct token provided`() {
        mvc.perform(get("/").header("Authorization", "bearer shared-secret"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.principal").value("aurora"))
            .andExpect(jsonPath("$.authorities").value("admin"))
    }
}

@WebMvcTest(TestController::class, properties = ["aurora.authentication.token.value=shared-secret", "aurora.authentication.enabled=true"])
class WebSecurityAuroraTokenValueTest @Autowired constructor(mvc: MockMvc) : WebSecurityConfigTest(mvc)

@WebMvcTest(
    TestController::class,
    properties = ["aurora.authentication.token.location=./src/test/resources/aurora-token", "aurora.authentication.enabled=true"]
)
class WebSecurityAuroraTokenLocationTest @Autowired constructor(mvc: MockMvc) : WebSecurityConfigTest(mvc)

@WebMvcTest(
    TestController::class,
    properties = ["aurora.authentication.token.value=shared-secret", "aurora.authentication.enabled=false"]
)
class WebSecuritySecurityDisabledTest @Autowired constructor(mvc: MockMvc) : WebSecurityConfigTest(mvc, AUTH_DISABLED)
