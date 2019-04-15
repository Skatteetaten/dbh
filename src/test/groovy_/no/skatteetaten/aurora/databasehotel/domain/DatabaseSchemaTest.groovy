package no.skatteetaten.aurora.databasehotel.domain

import no.skatteetaten.aurora.databasehotel.DomainUtils
import spock.lang.Specification

class DatabaseSchemaTest extends Specification {

  def "Removes existing user when adding"() {

    given:
      def schema = DomainUtils.createDatabaseSchema()

    expect:
      schema.users.size() == 0

    when:
      schema.addUser(new User("ID", "A", "-", "SCHEMA"))
      schema.addUser(new User("ID", "B", "-", "SCHEMA"))

    then:
      schema.users.size() == 2

    when:
      schema.addUser(new User("ID", "A", "PASS", "SCHEMA"))

    then:
      schema.users.size() == 2
      schema.users.find { it.name == "A" }.password == "PASS"
  }

}
