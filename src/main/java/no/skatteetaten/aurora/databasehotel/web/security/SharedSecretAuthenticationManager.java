package no.skatteetaten.aurora.databasehotel.web.security;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class SharedSecretAuthenticationManager implements AuthenticationManager {

    private Pattern headerPattern;

    private String prenegotiatedSecret;

    public SharedSecretAuthenticationManager(String prenegotiatedSecret, String headerValuePrefix) {

        if (isNullOrEmpty(prenegotiatedSecret)) {
            throw new IllegalArgumentException("Prenegotiated secret cannot be null");
        }
        if (isNullOrEmpty(headerValuePrefix)) {
            throw new IllegalArgumentException("headerValuePrefix cannot be null");
        }
        this.prenegotiatedSecret = prenegotiatedSecret;

        headerPattern = Pattern.compile(headerValuePrefix + "\\s+(.*)", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public Authentication authenticate(Authentication authentication) {

        String authenticationHeaderValue = authentication.getPrincipal().toString();
        Matcher matcher = headerPattern.matcher(authenticationHeaderValue);
        if (!matcher.find()) {
            throw new BadCredentialsException("Unexpected Authorization header format");
        }
        String token = matcher.group(1);
        if (!prenegotiatedSecret.equals(token)) {
            throw new BadCredentialsException("Invalid bearer token");
        }

        return new PreAuthenticatedAuthenticationToken("aurora", authentication.getCredentials(),
            newArrayList((GrantedAuthority) () -> "admin"));
    }
}
