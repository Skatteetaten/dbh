package no.skatteetaten.aurora.databasehotel.service.oracle

import static no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseInstanceInitializer.DEFAULT_SCHEMA_NAME

import javax.sql.DataSource

import groovy.sql.Sql
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.dao.oracle.AbstractOracleSpec
import no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.oracle.Datasources
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance
import no.skatteetaten.aurora.databasehotel.service.ResourceUsageCollector
import spock.lang.Shared

class OracleResourceUsageCollectorTest extends AbstractOracleSpec {

  @Shared
  OracleResourceUsageCollector resourceUsageCollector

  static final List<String> TEST_SCHEMAS = ["TEST1", "TEST2"]

  def setupSpec() {

    this.resourceUsageCollector = new OracleResourceUsageCollector(managerDs, 300000L)

    def databaseManager = new OracleDatabaseManager(managerDs)
    def dataSource = cleanUpPreviousRun(databaseManager)

    def databaseHotelDataDao = new OracleDatabaseHotelDataDao(dataSource)
    def databaseInstance = new DatabaseInstance(new DatabaseInstanceMetaInfo("a", "b", 1512), databaseManager,
        databaseHotelDataDao, new OracleJdbcUrlBuilder("dbhotel"), resourceUsageCollector, true)

    createTestSchemas(databaseInstance, databaseManager)
  }

  def "Get schema sizes"() {
    when:
      resourceUsageCollector.invalidateCache()
      List<ResourceUsageCollector.SchemaSize> schemaSizes = resourceUsageCollector.getSchemaSizes()

    then:
      schemaSizes.size() == 2
      schemaSizes.find { it.owner == "TEST1" }.schemaSizeMb > 0.0
      schemaSizes.find { it.owner == "TEST2" }.schemaSizeMb > 0.0
  }

  def "Get schema size"() {
    when:
      resourceUsageCollector.invalidateCache()
      Optional<ResourceUsageCollector.SchemaSize> schemaSize = resourceUsageCollector.getSchemaSize("TEST1")
    then:
      schemaSize.get().owner == "TEST1"
      schemaSize.get().schemaSizeMb > 0.0
  }

  def "Get schema size for unmanaged schema fails"() {
    when:
      resourceUsageCollector.invalidateCache()
      Optional<ResourceUsageCollector.SchemaSize> schemaSize = resourceUsageCollector.getSchemaSize("SYS")

    then:
      !schemaSize.isPresent()
  }

  private static void createTestSchemas(DatabaseInstance databaseInstance, DatabaseManager databaseManager) {
    TEST_SCHEMAS.each { tableName ->
      databaseInstance.createSchema(tableName, null)
      databaseManager.executeStatements("create table ${tableName}.DUMMY (id integer)")
      (1..10).each {
        databaseManager.executeStatements("insert into ${tableName}.DUMMY values (${it})")
      }
    }
  }

  private static DataSource cleanUpPreviousRun(DatabaseManager databaseManager) {

    databaseManager.deleteSchema(DEFAULT_SCHEMA_NAME)
    databaseManager.createSchema(DEFAULT_SCHEMA_NAME, DEFAULT_SCHEMA_NAME)
    def dataSource = Datasources.createTestDs(DEFAULT_SCHEMA_NAME, DEFAULT_SCHEMA_NAME)

    TEST_SCHEMAS.each { tableName ->
      try {
        databaseManager.deleteSchema(tableName)
      } catch (Exception e) {
      }
    }

    def initializer = new DatabaseInstanceInitializer()
    initializer.assertInitialized(databaseManager, DEFAULT_SCHEMA_NAME)
    initializer.migrate(dataSource)

    dataSource
  }
}
