package no.skatteetaten.aurora.databasehotel.web.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

@EnableWebSecurity
@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private SharedSecretReader sharedSecretReader;

    @Value("${aurora.token.headerValuePrefix:}")
    private String headerValuePrefix;

    @Value("${aurora.authenticationEnabled:true}")
    private boolean authenticationEnabled;

    @Value("${management.server.port:8081}")
    private Integer managementPort;

    @Bean
    SharedSecretAuthenticationManager sharedSecretAuthenticationManager() {

        String secret = authenticationEnabled ? sharedSecretReader.getSecret() : "-";
        return new SharedSecretAuthenticationManager(secret, headerValuePrefix);
    }

    @Bean
    RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter() {

        RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter = new RequestHeaderAuthenticationFilter();
        requestHeaderAuthenticationFilter.setPrincipalRequestHeader("Authorization");
        requestHeaderAuthenticationFilter.setExceptionIfHeaderMissing(false);
        requestHeaderAuthenticationFilter.setAuthenticationManager(sharedSecretAuthenticationManager());

        return requestHeaderAuthenticationFilter;
    }

    @Bean
    PreAuthenticatedAuthenticationProvider preAuthenticationProvider() {

        PreAuthenticatedAuthenticationProvider authenticationProvider = new PreAuthenticatedAuthenticationProvider();
        authenticationProvider.setPreAuthenticatedUserDetailsService(
            token -> new User((String) token.getPrincipal(), (String) token.getCredentials(), token.getAuthorities()));
        return authenticationProvider;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http.csrf().disable();


        if (authenticationEnabled) {
            http.authenticationProvider(preAuthenticationProvider())
                .addFilter(requestHeaderAuthenticationFilter())
                .authorizeRequests()
                .requestMatchers(request -> managementPort == request.getLocalPort()).permitAll()
                .antMatchers(HttpMethod.GET, "/docs/**").permitAll()
                .anyRequest().hasAuthority("admin");
        }
    }
}
