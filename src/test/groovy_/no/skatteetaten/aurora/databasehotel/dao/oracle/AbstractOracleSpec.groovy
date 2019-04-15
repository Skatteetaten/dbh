package no.skatteetaten.aurora.databasehotel.dao.oracle

import com.zaxxer.hikari.HikariDataSource

import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractOracleSpec extends Specification {

  @Shared
  HikariDataSource managerDs

  def setupSpec() {

    managerDs = Datasources.createTestDs()
  }
}
