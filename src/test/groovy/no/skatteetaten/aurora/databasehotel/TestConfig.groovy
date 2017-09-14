package no.skatteetaten.aurora.databasehotel

import org.springframework.boot.devtools.env.DevToolsHomePropertiesPostProcessor
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.env.MockEnvironment

/**
 * When opensourcing the database hotel api module we wanted to remove all references to internal servers, usernames
 * and passwords from the git repository. This was approached by using the spring-boot-devtools home properties
 * feature, where you can create a file, ~/.spring-boot-devtools.properties, that will be included whenever you
 * start a spring boot application or a spring boot test. This works seamlessly for everything that is spring boot,
 * but several of the tests in the application does not require or use spring boot at all. This class loads test
 * configuration properties in a similar way to that used by spring boot devtools, so that all tests can use the
 * property overrides set in the ~/.spring-boot-devtools.properties file.
 */
class TestConfig {
  static Environment loadTestConfig() {
    def environment = new MockEnvironment().with {
      def propertySource = new YamlPropertySourceLoader().
          load("test-properties", new ClassPathResource("application.yml"), null)
      getPropertySources().addFirst(propertySource)
      new DevToolsHomePropertiesPostProcessor().postProcessEnvironment(it, null)
      it
    }
    environment
  }
}
