package no.skatteetaten.aurora.databasehotel.service

import no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import spock.lang.Specification

class DatabaseHotelAdminServiceTest extends Specification {

  def adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db", 300000L)

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
      adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db", 300000L)
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

    given:
      adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, "db", 300000L)

    [createMockInstance("dev1", true),
     createMockInstance("dev2", true),
     createMockInstance("dev3", false)
    ].each {
      adminService.registerDatabaseInstance(it)
    }

    expect:
      def usedInstances = [] as Set
      (0..1000).each {
        def instance = adminService.findDatabaseInstanceOrFail(null)
        assert instance != null
        assert instance.instanceName != "dev3"
        usedInstances << instance.instanceName
      }

      usedInstances.containsAll(["dev1", "dev2"])
  }

  private def createMockInstance(String name, boolean isCreateSchemaAllowed) {
    def databaseInstance = Mock(DatabaseInstance)
    databaseInstance.getMetaInfo() >> new DatabaseInstanceMetaInfo(name, "$name-localhost", 1521)
    databaseInstance.getInstanceName() >> name
    databaseInstance.isCreateSchemaAllowed() >> isCreateSchemaAllowed
    return databaseInstance
  }

  def "Specifying a missing instance fails"() {

    when:
      adminService.findDatabaseInstanceOrFail("relay")

    then:
      thrown(DatabaseServiceException)
  }
}
