package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseInstanceInitializer
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

  def "Delete schema for nonexisting instance"() {

    when:
      new DatabaseHotelService(new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db", 300000L)).
          deleteSchema("nosuchinstance", "does not matter")
      new DatabaseHotelService(new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db", 300000L)).deleteSchema("nosuchinstance", "does not matter")

    then:
      thrown(DatabaseServiceException)
  }

  def "Create schema for non existing instance"() {

    when:
      new DatabaseHotelService(new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db", 300000L)).
          createSchema("nosuchlevel")

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
      1 * databaseInstance.createSchema(labels) >>
          new DatabaseSchema(null, null, null, null, null, null, new DatabaseSchemaMetaData(0.0))
  }

  def "Update schema labels"() {

    given:
      def databaseInstance = Mock(DatabaseInstance)

      adminService.findAllDatabaseInstances() >> [databaseInstance]
      def databaseSchema = new DatabaseSchema("some_id", null, null, null, null, null, null)
      databaseInstance.findSchemaById(databaseSchema.id) >> Optional.of(databaseSchema)

      Map<String, String> labels = [deploymentId: "TestDeployment"]

    when:
      databaseHotelService.updateSchema(databaseSchema.id, labels)

    then:
      1 * databaseInstance.replaceLabels(databaseSchema, labels)
  }
}
