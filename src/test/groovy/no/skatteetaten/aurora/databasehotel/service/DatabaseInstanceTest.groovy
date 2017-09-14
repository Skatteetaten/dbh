package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.dto.Schema
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import spock.lang.Specification

class DatabaseInstanceTest extends Specification {

  DatabaseInstanceMetaInfo databaseInstanceMetaInfo = new DatabaseInstanceMetaInfo('dev', "localhost", 1521)

  def databaseDao = Mock(OracleDatabaseManager)

  def databaseHotelDataDao = Mock(OracleDatabaseHotelDataDao)

  def integration = Mock(Integration)

  def resourceUsageCollector = Mock(ResourceUsageCollector)

  def databaseInstance = new DatabaseInstance(databaseInstanceMetaInfo,
      databaseDao, databaseHotelDataDao, Mock(JdbcUrlBuilder), resourceUsageCollector).with {
    it.registerIntegration(integration)
    it
  }

  def "Create schema"() {

    given:
      def schemaName = "ANY_SCHEMA"
      def password = "any_pass"

      databaseDao.createSchema(schemaName, password) >> schemaName
      databaseDao.findSchemaByName(schemaName) >> Optional.of(new Schema(username: schemaName))
      databaseHotelDataDao.createSchemaData(schemaName) >> new SchemaData(id: "A", name: schemaName)
      databaseHotelDataDao.findSchemaDataByName(schemaName) >> Optional.of(new SchemaData(id: "A", name: schemaName))
      databaseHotelDataDao.findAllUsersForSchema("A") >> [
          new SchemaUser(id: "USER", username: schemaName, password: password, type: "SCHEMA")
      ]
      1 * integration.onSchemaCreated({ it.name == schemaName })
      resourceUsageCollector.getSchemaSize(_) >> Optional.empty()

    when:
      DatabaseSchema databaseSchema = databaseInstance.createSchema(schemaName, password, null)

    then:
      databaseSchema.id == "A"
      databaseSchema.users.size() == 1
      databaseSchema.users[0].with {
        assert name == schemaName
        assert it.password == password
        assert type == "SCHEMA"
        true
      }
  }

  def "Replace labels"() {

    given:
      DatabaseSchema schema = new DatabaseSchema("id", databaseInstanceMetaInfo, "", "", new Date(), new Date(),
          new DatabaseSchemaMetaData(0.0))

    when:
      databaseInstance.replaceLabels(schema, [deploymentId: "SomeDeploymentId", otherLabel: "KLJ"])

    then:
      schema.labels == [deploymentId: "SomeDeploymentId", otherLabel: "KLJ"]
      1 * databaseHotelDataDao.replaceLabels(schema.id, [deploymentId: "SomeDeploymentId", otherLabel: "KLJ"])

    when:
      databaseInstance.replaceLabels(schema, [deploymentId: "SomeDeploymentId"])

    then:
      schema.labels == [deploymentId: "SomeDeploymentId"]
      1 * databaseHotelDataDao.replaceLabels(schema.id, [deploymentId: "SomeDeploymentId"])
  }

  def "Replace labels with empty or null removes all labels"() {

    given:
      DatabaseSchema schema = new DatabaseSchema("id", databaseInstanceMetaInfo, "", "", new Date(), new Date(),
          new DatabaseSchemaMetaData(0.0))

    when:
      databaseInstance.replaceLabels(schema, null)

    then:
      schema.labels == [:]

    when:
      databaseInstance.replaceLabels(schema, [:])

    then:
      schema.labels == [:]
  }
}
