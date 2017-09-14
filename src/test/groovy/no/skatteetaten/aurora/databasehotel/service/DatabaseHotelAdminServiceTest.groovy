package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import spock.lang.Specification

class DatabaseHotelAdminServiceTest extends Specification {

  def adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db")

  def setup() {

    def databaseInstance1 = Mock(DatabaseInstance)
    databaseInstance1.getMetaInfo() >> new DatabaseInstanceMetaInfo("test", "localhost", 1521)
    databaseInstance1.getInstanceName() >> "test"
    def databaseInstance2 = Mock(DatabaseInstance)
    databaseInstance2.getMetaInfo() >> new DatabaseInstanceMetaInfo("prod", "remotehost", 1521)
    databaseInstance2.getInstanceName() >> "prod"

    adminService.registerDatabaseInstance(databaseInstance1)
    adminService.registerDatabaseInstance(databaseInstance2)
  }

  def "Register database instance"() {

    given:
      adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db")
      def databaseInstance = Mock(DatabaseInstance)
      databaseInstance.getMetaInfo() >> new DatabaseInstanceMetaInfo("test", "localhost", 1521)

    expect:
      adminService.findAllDatabaseInstances().empty

    when:
      adminService.registerDatabaseInstance(databaseInstance)

    then:
      !adminService.findAllDatabaseInstances().empty
  }

  def "Find database instance by host"() {

    expect:
      adminService.findDatabaseInstanceByHost("localhost").get().metaInfo.host == "localhost"
      adminService.findDatabaseInstanceByHost("remotehost").get().metaInfo.host == "remotehost"

    when:
      Optional<DatabaseInstance> databaseInstance = adminService.findDatabaseInstanceByHost("nosuchhost")

    then:
      !databaseInstance.isPresent()
  }

  def "Find database instance by instance"() {

    expect:
      adminService.findDatabaseInstanceByInstanceName("test").get().metaInfo.host == "localhost"
      adminService.findDatabaseInstanceByInstanceName("prod").get().metaInfo.host == "remotehost"

    when:
      Optional<DatabaseInstance> databaseInstance = adminService.findDatabaseInstanceByInstanceName("dev")

    then:
      !databaseInstance.isPresent()
  }

  def "Not specifying instance fails when several instances exist"() {

    when:
      adminService.findDatabaseInstanceOrFail(null)

    then:
      thrown(DatabaseServiceException)
  }

  def "Specifying a missing instance fails"() {

    when:
      adminService.findDatabaseInstanceOrFail("relay")

    then:
      thrown(DatabaseServiceException)
  }
}
