package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import spock.lang.Specification

class DatabaseHotelServiceTest extends Specification {

  public static final String INSTANCE_NAME = "test-dev"

  def adminService = Mock(DatabaseHotelAdminService).with {
    it.getExternalSchemaManager() >> Optional.empty()
    it
  }

  def databaseHotelService = new DatabaseHotelService(adminService)

  def "Create schema for non existing instance"() {

    when:
      new DatabaseHotelService(new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, 1, "db", 300000L))
          .createSchema("nosuchlevel")

    then:
      thrown(DatabaseServiceException)
  }

  def "Create schema with labels"() {

    given:
      def databaseInstance = Mock(DatabaseInstance)

      adminService.findDatabaseInstanceOrFail(INSTANCE_NAME) >> databaseInstance

      Map<String, String> labels = [deploymentId: "TestDeployment"]

    when:
      databaseHotelService.createSchema(INSTANCE_NAME, labels)

    then:
      1 * databaseInstance.createSchema(labels) >> databaseSchema()
  }

  def "Update schema labels"() {

    given:
      def databaseInstance = Mock(DatabaseInstance)

      adminService.findAllDatabaseInstances() >> [databaseInstance]
      def databaseSchema = databaseSchema("some_id")
      databaseInstance.findSchemaById(databaseSchema.id) >> Optional.of(databaseSchema)

      Map<String, String> labels = [deploymentId: "TestDeployment"]

    when:
      databaseHotelService.updateSchema(databaseSchema.id, labels)

    then:
      1 * databaseInstance.replaceLabels(databaseSchema, labels)
  }

  private static DatabaseSchema databaseSchema(String id="id") {
    new DatabaseSchema(id, new no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo.DatabaseInstanceMetaInfo("name", "host", 0), "-", "-", new Date(), new Date(), new DatabaseSchemaMetaData(0.0))
  }
}
