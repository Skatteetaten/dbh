package no.skatteetaten.aurora.databasehotel.service.internal

import no.skatteetaten.aurora.databasehotel.DomainUtils
import spock.lang.Specification

class SchemaLabelMatcherTest extends Specification {

  def "Label matching"() {

    given:
      def databaseSchema = DomainUtils.createDatabaseSchema()
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
