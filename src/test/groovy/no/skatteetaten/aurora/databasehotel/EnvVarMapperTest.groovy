package no.skatteetaten.aurora.databasehotel

import spock.lang.Ignore
import spock.lang.Specification

class EnvVarMapperTest extends Specification {

  @Ignore("Cannot run this test in vm with other tests because env is manipulated")
  def "Verify variables are set"() {

    given:
      EnvironmentUtils.setEnv([
          DATABASE_CONFIG_DATABASES_0_host         : 'dbhost1.example.com',
          DATABASE_CONFIG_DATABASES_0_service      : 'dbhotel',
          DATABASE_CONFIG_DATABASES_0_instanceName : 'test-dev',
          DATABASE_CONFIG_DATABASES_0_username     : 'user',
          DATABASE_CONFIG_DATABASES_0_password     : 'pass',
          DATABASE_CONFIG_DATABASES_0_clientService: 'dbhotel',
          DATABASECONFIG_DATABASES_1_host          : 'dbhost1.example.com',
          DATABASECONFIG_DATABASES_1_service       : 'dbhotel',
          DATABASECONFIG_DATABASES_1_instanceName  : 'test-dev',
          DATABASECONFIG_DATABASES_1_username      : 'user',
          DATABASECONFIG_DATABASES_1_password      : 'pass',
          DATABASECONFIG_DATABASES_1_clientService : 'dbhotel'
      ])

    expect:
      !System.properties.getProperty("databaseConfig")

    when:
      EnvVarMapper.mapEnvironmentVarsToSystemProperties()

    then:
      def props = System.properties.findAll { it ==~ /databaseConfig.databases\[\d+\].*/ }
      props.size() == 12
  }
}
