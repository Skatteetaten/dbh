package no.skatteetaten.aurora.databasehotel.web.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher

@EnableWebSecurity
@Configuration
class WebSecurityConfig(
    val sharedSecretReader: SharedSecretReader,
    @Value("\${management.server.port}") val managementPort: Int,
    @Value("\${aurora.authentication.enabled:true}") val authenticationEnabled: Boolean,
    @Value("\${aurora.authentication.token.header-value-prefix:bearer|aurora-token}") val headerValuePrefixesRegexFormat: String
) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()

        http.authenticationProvider(preAuthenticationProvider())
            .addFilter(requestHeaderAuthenticationFilter())
            .authorizeRequests()
            .requestMatchers(RequestMatcher { managementPort == it.localPort }).permitAll()
            .anyRequest().also {
                if (authenticationEnabled) it.authenticated()
                else it.permitAll()
            }
    }

    private fun preAuthenticationProvider(): PreAuthenticatedAuthenticationProvider? =
        PreAuthenticatedAuthenticationProvider().apply {
            setPreAuthenticatedUserDetailsService { token: PreAuthenticatedAuthenticationToken ->
                User(
                    token.principal as String,
                    token.credentials as String,
                    token.authorities
                )
            }
        }

    @Bean
    fun requestHeaderAuthenticationFilter() =
        RequestHeaderAuthenticationFilter().apply {
            setPrincipalRequestHeader("Authorization")
            setExceptionIfHeaderMissing(false)
            setAuthenticationManager(
                SharedSecretAuthenticationManager(
                    prenegotiatedSecret = if(authenticationEnabled) sharedSecretReader.secret else "-",
                    headerValuePrefixesRegexFormat = headerValuePrefixesRegexFormat,
                    authIsEnabled = authenticationEnabled
                )
            )
        }
}
