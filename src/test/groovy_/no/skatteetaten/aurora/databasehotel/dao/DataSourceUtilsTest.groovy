package no.skatteetaten.aurora.databasehotel.dao

import spock.lang.Specification

class DataSourceUtilsTest extends Specification {

  def "Generate password hint"() {

    expect:
      DataSourceUtils.createPasswordHint(password) == hint

    where:
      password          | hint
      null              | ""
      ""                | ""
      "AAA"             | "***"
      "1234567"         | "*******"
      "12345678"        | "12****78"
      "12345678ABCDEFG" | "12***********FG"
  }
}
