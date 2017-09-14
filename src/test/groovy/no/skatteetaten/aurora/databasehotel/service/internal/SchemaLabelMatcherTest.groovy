package no.skatteetaten.aurora.databasehotel.service.internal

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import spock.lang.Specification

public class SchemaLabelMatcherTest extends Specification {

  def "Label matching"() {

    given:
      def databaseSchema = new DatabaseSchema(null, null, null, null, null, null, new DatabaseSchemaMetaData(0.0))
      databaseSchema.labels = schemaLabels

    expect:
      SchemaLabelMatcher.matchesAll(databaseSchema, searchLabels) == expectedMatch

    where:
      schemaLabels            | searchLabels                                | expectedMatch
      [:]                     | [deploymentId: "DEPID"]                     | false
      [deploymentId: "DEPID"] | [:]                                         | true
      [deploymentId: "DEPID"] | [deploymentId: "DEPID"]                     | true
      [deploymentId: "DEPID"] | [deploymentId: "DEPI"]                      | false
      [deploymentId: "DEPID"] | [deploymentId: "DEPID", other: "SOMETHING"] | false
  }
}
