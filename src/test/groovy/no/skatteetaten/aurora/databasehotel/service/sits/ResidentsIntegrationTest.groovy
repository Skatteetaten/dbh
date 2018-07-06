package no.skatteetaten.aurora.databasehotel.service.sits

import com.zaxxer.hikari.HikariDataSource

import groovy.sql.Sql
import no.skatteetaten.aurora.databasehotel.dao.oracle.Datasources
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import spock.lang.Shared
import spock.lang.Specification

class ResidentsIntegrationTest extends Specification {

  @Shared
  HikariDataSource dataSource = Datasources.createTestDs()

  def integration = new ResidentsIntegration(dataSource)

  DatabaseSchema schema = new DatabaseSchema("id", new DatabaseInstanceMetaInfo("A", "B", 1234), "jdbc", "schema_name",
      new Date(), new Date(), new DatabaseSchemaMetaData(0.0)).with {
    it.setLabels([userId: 'k77319', affiliation: 'aurora', environment: 'dev', application: 'dbh', 'name': 'db'])
    it
  }

  def "Fails when required labels not specified"() {

    given:
      schema.labels = [:]

    when:
      integration.onSchemaCreated(schema)

    then:
      thrown(IllegalArgumentException)
  }

  def "Inserts into resident table onSchemaCreated"() {

    given:
      def sql = new Sql(dataSource)
      sql.execute("delete from RESIDENTS.RESIDENTS")

    when:
      integration.onSchemaCreated(schema)

    then:
      def resident = sql.firstRow("select * from RESIDENTS.RESIDENTS")
      resident.RESIDENT_NAME == schema.name
      resident.RESIDENT_EMAIL == 'k77319'
      resident.RESIDENT_SERVICE == 'aurora/dev/dbh/db'
  }
}
