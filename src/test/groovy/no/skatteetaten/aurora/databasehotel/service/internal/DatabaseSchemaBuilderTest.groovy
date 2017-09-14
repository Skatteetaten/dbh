package no.skatteetaten.aurora.databasehotel.service.internal

import no.skatteetaten.aurora.databasehotel.dao.dto.Schema
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.JdbcUrlBuilder
import spock.lang.Specification

class DatabaseSchemaBuilderTest extends Specification {

  def builder = new DatabaseSchemaBuilder(new DatabaseInstanceMetaInfo("test", "localhost", 1521), Mock(JdbcUrlBuilder))

  def "Returns empty list when no schema data is specified"() {

    when:
      Set<DatabaseSchema> schemas = builder.createMany([], [], [], [], [])

    then:
      schemas.isEmpty()
  }

  def "Combines data correctly"() {

    when:
      Set<DatabaseSchema> schemas = builder.createMany([
          new SchemaData(id: 'A', name: 'SCHEMA_NAME')
      ], [
          new Schema(username: 'SCHEMA_NAME', lastLogin: new Date(), created: new Date())
      ], [], [], [])

    then:
      schemas.size() == 1
      schemas[0].name == 'SCHEMA_NAME'
      schemas[0].id == 'A'
      schemas[0].createdDate != null
  }

  def "Skips schemas with missing schema data"() {

    when:
      Set<DatabaseSchema> schemas = builder.createMany([
          new SchemaData(id: 'A', name: 'SCHEMA_NAME')
      ], [
          new Schema(username: 'SCHEMA_NAME', lastLogin: new Date(), created: new Date()),
          new Schema(username: 'SCHEMA_NAME_WITH_NO_MATCH', lastLogin: new Date(), created: new Date())
      ], [], [], [])

    then:
      schemas.size() == 1
      schemas[0].name == 'SCHEMA_NAME'
      schemas[0].id == 'A'
      schemas[0].createdDate != null
  }
}
