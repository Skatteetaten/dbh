package no.skatteetaten.aurora.databasehotel.dao.oracle

import groovy.sql.Sql
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils

class OracleDatabaseManagerTest extends AbstractOracleSpec {

  public static final String SCHEMA_NAME = "SCHEMA_TO_CREATE"

  OracleDatabaseManager databaseManager

  def setup() {

    databaseManager = new OracleDatabaseManager(managerDs)
  }

  def "Find all schemas does not include connecting user"() {

    expect:
      def schemas = databaseManager.findAllNonSystemSchemas()
      assert !schemas*.username.contains(managerDs.username.toUpperCase())
  }

  def "Find all schemas does not include system schemas"() {

    expect:
      def schemas = databaseManager.findAllNonSystemSchemas()
      def systemSchemas = ['ORACLE_OCM', 'SYSBACKUP', 'XS$NULL', 'ADDM_USER', 'SYSKM', 'OJVMSYS', 'AUDSYS',
                           'GSMCATUSER', 'GSMUSER', 'SYSDG', 'DIP', 'DBDRIFT']
      !systemSchemas.any { schemas*.username.contains(it) }
  }

  def "Test create and delete schema"() {

    given: "Make sure the schema to create does not exist in the database"

      databaseManager.deleteSchema(SCHEMA_NAME)

      def allSchemasPre = databaseManager.findAllNonSystemSchemas()

    when:
      databaseManager.createSchema(SCHEMA_NAME, "pass")

    then:
      !allSchemasPre.find { it.username == SCHEMA_NAME }

      def allSchemasAfter = databaseManager.findAllNonSystemSchemas()
      allSchemasAfter.find { it.username == SCHEMA_NAME }

    when:
      databaseManager.deleteSchema(SCHEMA_NAME)

    then:
      def allSchemasAfterDelete = databaseManager.findAllNonSystemSchemas()
      !allSchemasAfterDelete.find { it.username == SCHEMA_NAME }
  }

  def "Can modify created schema with schema user"() {

    given: "Make sure the schema to create does not exist in the database"
      if (databaseManager.schemaExists(SCHEMA_NAME)) {
        databaseManager.deleteSchema(SCHEMA_NAME)
      }

    when:
      databaseManager.createSchema(SCHEMA_NAME, "pass")
      Sql sql = new Sql(DataSourceUtils.createDataSource(managerDs.getJdbcUrl(), SCHEMA_NAME, "pass"))
      assert sql.rows("SELECT table_name FROM user_tables").size() == 0

      sql.execute('''CREATE TABLE customers ( customer_id number(10) NOT NULL, customer_name varchar2(50) )''')

    then:
      def tables = sql.rows("SELECT table_name FROM user_tables")
      tables.size() == 1
      tables.any { it.TABLE_NAME == 'CUSTOMERS' }
  }
}
