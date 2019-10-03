package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import spock.lang.Specification

class DbhInitializerTest extends Specification {

  static testConfig = TestConfig.loadTestConfig()

  def databaseHotelAdminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, 1, "test-dev", 30000L)

  def dbConfig = [
      "dbhost.example.com", // This server does not exist an cannot be connected to
      testConfig.getProperty("database-config.databases[0].host")
  ].collect { host ->
    [
        host         : host,
        service      : testConfig.getProperty("database-config.databases[0].service"),
        instanceName : "test-dev",
        username     : testConfig.getProperty("database-config.databases[0].username"),
        password     : testConfig.getProperty("database-config.databases[0].password"),
        clientService: testConfig.getProperty("database-config.databases[0].clientService")
    ]
  }

  def setup() {

    LoggingUtils.setLogLevels()
  }

  def "Retries configuration till server becomes available"() {

    def configurator = new DbhInitializer(databaseHotelAdminService, new DbhConfiguration(databases: dbConfig), 500)

    when:
      def configureThread = new Thread({ configurator.configure() })
      configureThread.start()

      // This thread will wait for a little while before correcting the configuration of the dbhost that does not
      // exist, simulating it "coming back online".
      def configCorrectorThread = new Thread({
        Thread.sleep(2000)
        dbConfig[0].host = dbConfig[1].host
      })
      configCorrectorThread.start()

    then:
      // If the configuration has not been completed in 10 seconds there is something fishy going on...
      configureThread.join(10000)
  }
}
