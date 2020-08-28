package no.skatteetaten.aurora.databasehotel.web.security

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken

class SharedSecretAuthenticationManager(
    private val prenegotiatedSecret: String,
    private val headerValuePrefixesRegexFormat: String,
    private val authIsEnabled: Boolean
) : AuthenticationManager {
    override fun authenticate(authentication: Authentication): Authentication {
        if (authIsEnabled) {
            val authenticationHeaderValue = authentication.principal.toString()

            val headerFormatRegex = Regex("($headerValuePrefixesRegexFormat)\\s+(.*)", RegexOption.IGNORE_CASE)
            if (!headerFormatRegex.matches(authenticationHeaderValue))
                throw BadCredentialsException("Unexpected Authorization header format")

            val tokenFromRequest = authenticationHeaderValue
                .replace(Regex(headerValuePrefixesRegexFormat, RegexOption.IGNORE_CASE), "")
                .trim()

            if (tokenFromRequest != prenegotiatedSecret) throw BadCredentialsException("Invalid bearer token")
        }

        return PreAuthenticatedAuthenticationToken(
            "aurora", authentication.credentials,
            listOf(SimpleGrantedAuthority("admin"))
        )
    }
}
