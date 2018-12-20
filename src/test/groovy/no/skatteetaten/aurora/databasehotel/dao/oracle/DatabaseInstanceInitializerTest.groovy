package no.skatteetaten.aurora.databasehotel.dao.oracle

import static no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer.DEFAULT_SCHEMA_NAME

import org.springframework.jdbc.core.JdbcTemplate

import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer

class DatabaseInstanceInitializerTest extends AbstractOracleSpec {

  def "Creates data schema and initializes it"() {

    given:
      def databaseManager = new OracleDatabaseManager(managerDs)
      databaseManager.deleteSchema(DEFAULT_SCHEMA_NAME)

      def initializer = new DatabaseInstanceInitializer()

    expect:
      !databaseManager.schemaExists(DEFAULT_SCHEMA_NAME)

    when:
      initializer.assertInitialized(databaseManager, DEFAULT_SCHEMA_NAME)
      initializer.migrate(managerDs)

    then:
      databaseManager.schemaExists(DEFAULT_SCHEMA_NAME)
      databaseManager.executeStatements("ALTER SESSION SET CURRENT_SCHEMA=$DEFAULT_SCHEMA_NAME")
      new JdbcTemplate(managerDs).queryForList("select * from SCHEMA_VERSION").max { it.installed_rank }.installed_rank == 9
  }
}
