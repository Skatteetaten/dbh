package no.skatteetaten.aurora.databasehotel.service

import static no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE
import static no.skatteetaten.aurora.databasehotel.DatabaseEngine.POSTGRES
import static no.skatteetaten.aurora.databasehotel.DomainUtils.createDatabaseInstanceMetaInfo

import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import spock.lang.Specification

class DatabaseHotelAdminServiceTest extends Specification {

  def adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, 1, "db", 300000L)

  def setup() {

    def databaseInstance1 = Mock(DatabaseInstance)
    databaseInstance1.getMetaInfo() >> createDatabaseInstanceMetaInfo("test", "localhost", 1521)
    databaseInstance1.getInstanceName() >> "test"
    def databaseInstance2 = Mock(DatabaseInstance)
    databaseInstance2.getMetaInfo() >> createDatabaseInstanceMetaInfo("prod", "remotehost", 1521, true)
    databaseInstance2.getInstanceName() >> "prod"

    adminService.registerDatabaseInstance(databaseInstance1)
    adminService.registerDatabaseInstance(databaseInstance2)
  }

  def "Register database instance"() {

    given:
      adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, 1, "db", 300000L)
      def databaseInstance = Mock(DatabaseInstance)
      databaseInstance.getMetaInfo() >> createDatabaseInstanceMetaInfo("test", "localhost", 1521, true)

    expect:
      adminService.findAllDatabaseInstances().empty

    when:
      adminService.registerDatabaseInstance(databaseInstance)

    then:
      !adminService.findAllDatabaseInstances().empty
  }

  def "Find database instance by host"() {

    expect:
      adminService.findDatabaseInstanceByHost("localhost").metaInfo.host == "localhost"
      adminService.findDatabaseInstanceByHost("remotehost").metaInfo.host == "remotehost"

    when:
      DatabaseInstance databaseInstance = adminService.findDatabaseInstanceByHost("nosuchhost")

    then:
      !databaseInstance
  }

  def "Find database instance by name"() {

    expect:
      adminService.findDatabaseInstanceByInstanceName("test").metaInfo.host == "localhost"
      adminService.findDatabaseInstanceByInstanceName("prod").metaInfo.host == "remotehost"

    when:
      DatabaseInstance databaseInstance = adminService.findDatabaseInstanceByInstanceName("dev")

    then:
      !databaseInstance
  }

  def "Get random instance when not specifying any instance requirements"() {

    given:
      adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, 1, "db", 300000L)

      [createMockInstance("dev1", true),
       createMockInstance("dev2", true),
       createMockInstance("dev3", false)
      ].each {
        adminService.registerDatabaseInstance(it)
      }

    expect:
      def usedInstances = [] as Set
      (0..1000).each {
        def instance = adminService.findDatabaseInstanceOrFail()
        assert instance != null
        assert instance.instanceName != "dev3"
        usedInstances << instance.instanceName
      }

      usedInstances.containsAll(["dev1", "dev2"])
  }

  def "Get random instance matching requirements when specifying engine"() {

    given:
      adminService = new DatabaseHotelAdminService(new DatabaseInstanceInitializer(), 6, 1, "db", 300000L)

      [
          createMockInstance("dev1", true, POSTGRES),
          createMockInstance("dev2", true, ORACLE),
          createMockInstance("dev3", false, POSTGRES),
          createMockInstance("dev4", true, ORACLE),
          createMockInstance("dev5", true, POSTGRES),
          createMockInstance("dev6", false, ORACLE)
      ].each {
        adminService.registerDatabaseInstance(it)
      }

    expect:
      def usedInstances = (0..1000).collect {
        adminService.findDatabaseInstanceOrFail(new DatabaseInstanceRequirements(POSTGRES, null)).instanceName
      } as Set

      usedInstances == ["dev1", "dev5"] as Set
  }

  def "Specifying a missing instance fails"() {

    when:
      adminService.findDatabaseInstanceOrFail(new DatabaseInstanceRequirements(ORACLE, "relay"))

    then:
      thrown(DatabaseServiceException)
  }

  private def createMockInstance(String name, boolean isCreateSchemaAllowed, DatabaseEngine engine = ORACLE) {
    def databaseInstance = Mock(DatabaseInstance)
    databaseInstance.getMetaInfo() >>
        createDatabaseInstanceMetaInfo(name, "$name-localhost", 1521, isCreateSchemaAllowed, engine)
    databaseInstance.getInstanceName() >> name
    databaseInstance.isCreateSchemaAllowed() >> isCreateSchemaAllowed
    return databaseInstance
  }
}
