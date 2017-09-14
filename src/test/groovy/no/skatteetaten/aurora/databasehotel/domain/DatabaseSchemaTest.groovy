package no.skatteetaten.aurora.databasehotel.domain

import spock.lang.Specification

class DatabaseSchemaTest extends Specification {

  def "Removes existing user when adding"() {

    given:
      def schema = new DatabaseSchema("ID", new DatabaseInstanceMetaInfo("test", "localhost", 1521), "jdbc", "local",
          new Date(), new Date(), null)

    expect:
      schema.users.size() == 0

    when:
      schema.addUser(new User("ID", "A", null, "SCHEMA"))
      schema.addUser(new User("ID", "B", null, "SCHEMA"))

    then:
      schema.users.size() == 2

    when:
      schema.addUser(new User("ID", "A", "PASS", "SCHEMA"))

    then:
      schema.users.size() == 2
      schema.users.find { it.name == "A" }.password == "PASS"
  }
}
