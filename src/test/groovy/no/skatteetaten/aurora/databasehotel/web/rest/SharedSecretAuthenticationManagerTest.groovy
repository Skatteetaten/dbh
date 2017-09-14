package no.skatteetaten.aurora.databasehotel.web.rest

import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken

import no.skatteetaten.aurora.databasehotel.web.security.SharedSecretAuthenticationManager
import spock.lang.Specification

class SharedSecretAuthenticationManagerTest extends Specification {

  public static final String PRENEGOTIATED_SECRET = "PRENEGOTIATEDSECRET"

  def authenticationManager = new SharedSecretAuthenticationManager(PRENEGOTIATED_SECRET, "bearer")

  def "Authenticates when token matches prenegotiated secret"() {

    given:
      def token = new PreAuthenticatedAuthenticationToken("Bearer $PRENEGOTIATED_SECRET", "N/A",
          new ArrayList<GrantedAuthority>())
      token.authenticated = false

    when:
      def authentication = authenticationManager.authenticate(token)

    then:
      authentication.authenticated
      authentication.authorities.find { it.authority == 'admin' }
      authentication.principal == 'aurora'
  }

  def "Fails when header value is invalid"() {

    given:
      def token = new PreAuthenticatedAuthenticationToken("This aint a valid header format", "N/A",
          new ArrayList<GrantedAuthority>())
      token.authenticated = false

    when:
      authenticationManager.authenticate(token)

    then:
      thrown(BadCredentialsException)
  }

  def "Fails when token is invalid"() {

    given:
      def token = new PreAuthenticatedAuthenticationToken("Bearer ImJustGuessingSomething", "N/A",
          new ArrayList<GrantedAuthority>())
      token.authenticated = false

    when:
      authenticationManager.authenticate(token)

    then:
      thrown(BadCredentialsException)
  }
}
