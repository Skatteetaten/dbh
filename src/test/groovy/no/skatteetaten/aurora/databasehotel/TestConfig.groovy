package no.skatteetaten.aurora.databasehotel

import org.springframework.boot.devtools.env.DevToolsHomePropertiesPostProcessor
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.env.MockEnvironment

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
