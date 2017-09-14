package no.skatteetaten.aurora.databasehotel.dao.oracle

import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import spock.lang.Shared

class OracleDatabaseHotelDataDaoTest extends AbstractOracleSpec {

  static final String SCHEMA_NAME = "DBHOTELSCHEMA"

  @Shared
  OracleDatabaseHotelDataDao hotelDataDao

  def setupSpec() {

    def databaseManager = new OracleDatabaseManager(managerDs)
    databaseManager.deleteSchema(SCHEMA_NAME)

    def initializer = new DatabaseInstanceInitializer(SCHEMA_NAME)
    initializer.assertInitialized(databaseManager, SCHEMA_NAME)
    initializer.migrate(managerDs)
    hotelDataDao = new OracleDatabaseHotelDataDao(Datasources.createTestDs(SCHEMA_NAME, SCHEMA_NAME))
  }

  def "Finds created schema data"() {

    when:
      SchemaData schema = hotelDataDao.createSchemaData('TEST')

    then:
      hotelDataDao.findSchemaDataById(schema.id).get().name == 'TEST'
  }

  def "Delete schema data"() {

    given:
      SchemaData schema = hotelDataDao.createSchemaData('TO_DELETE')

    expect:
      hotelDataDao.findSchemaDataById(schema.id).isPresent()

    when:
      hotelDataDao.deactivateSchemaData(schema.id)

    then:
      !hotelDataDao.findSchemaDataById(schema.id).isPresent()
  }

  def "Fails to create user for nonexisting schema"() {

    when:
      hotelDataDao.createUser("NOSUCHSCHEMAID", "SCHEMA", "A", "A")

    then:
      thrown(DataAccessException)
  }

  def "Create user"() {

    given:
      SchemaData schemaData = hotelDataDao.createSchemaData("SCHEMA_NAME")

    when:
      def user = hotelDataDao.createUser(schemaData.id, "SCHEMA", "SCHEMA_NAME", "PASS")

    then:
      user.id
      user.schemaId == schemaData.id
      user.type == "SCHEMA"
      user.username == "SCHEMA_NAME"
      user.password == "PASS"
  }

  def "Find users for schema"() {

    given:
      SchemaData schemaData1 = hotelDataDao.createSchemaData("SCHEMA_NAME_1")
      SchemaData schemaData2 = hotelDataDao.createSchemaData("SCHEMA_NAME_2")
      SchemaUser user1 = hotelDataDao.createUser(schemaData1.id, "SCHEMA", "SCHEMA_NAME_1", "PASS")
      SchemaUser user2 = hotelDataDao.createUser(schemaData1.id, "READWRITE", "SCHEMA_NAME_1_rw", "PASS")
      hotelDataDao.createUser(schemaData2.id, "SCHEMA", "SCHEMA_NAME_2", "PASS")

    when:
      List<SchemaUser> users = hotelDataDao.findAllUsersForSchema(schemaData1.id)

    then:
      users.size() == 2
      users*.id.containsAll(user1.id, user2.id)
  }

  def "Replace and find all labels"() {

    given:
      SchemaData schemaData = hotelDataDao.createSchemaData("SCHEMA_NAME_1")

    when:
      hotelDataDao.replaceLabels(schemaData.id, [deploymentId: "Test", otherLabel: "SomeValue"])

    then:
      def labels = hotelDataDao.findAllLabels()
      labels.size() == 2
      (labels*.schemaId as Set) == [schemaData.id] as Set
      labels.find { it.name == "deploymentId" }.value == "Test"
      labels.find { it.name == "otherLabel" }.value == "SomeValue"
  }
}
