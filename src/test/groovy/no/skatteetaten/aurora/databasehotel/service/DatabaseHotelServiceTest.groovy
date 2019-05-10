package no.skatteetaten.aurora.databasehotel.service
/*
import static no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE

import no.skatteetaten.aurora.databasehotel.domain.DomainUtils
import no.skatteetaten.aurora.databasehotel.hotelDataDao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import spock.lang.Specification

class DatabaseHotelServiceTest extends Specification {

  def adminService = Mock(DatabaseHotelAdminService)

  def databaseHotelService = new DatabaseHotelService(adminService)

  def requirements = new DatabaseInstanceRequirements(ORACLE, "test-dev", [:], false)

  def "Create schema for non existing instance"() {

    when:
      new DatabaseHotelService(new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, 1, "db", 300000L))
          .createSchema(new DatabaseInstanceRequirements(ORACLE, "nosuchlevel", [:], false))

    then:
      thrown(DatabaseServiceException)
  }

  def "Create schema with labels"() {

    given:
      def databaseInstance = Mock(DatabaseInstance)

      adminService.findDatabaseInstanceOrFail(requirements) >> databaseInstance

      Map<String, String> labels = [deploymentId: "TestDeployment"]

    when:
      databaseHotelService.createSchema(requirements, labels)

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

  private static DatabaseSchema databaseSchema(String id = "id") {
    return DomainUtils.createDatabaseSchema(id)
  }
}
*/